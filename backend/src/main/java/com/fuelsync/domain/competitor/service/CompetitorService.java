package com.fuelsync.domain.competitor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuelsync.common.exception.FuelSyncException;
import com.fuelsync.domain.competitor.model.CompetitorStation;
import com.fuelsync.domain.station.mapper.StationInfoMapper;
import com.fuelsync.domain.station.model.StationInfo;
import com.fuelsync.domain.tank.model.Tank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompetitorService {

    private final StationInfoMapper stationInfoMapper;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${opinet.api-key}")
    private String apiKey;

    private static final String OPINET_URL = "https://www.opinet.co.kr/api/aroundAll.do";

    /** 유종 → 오피넷 prodcd 코드 변환 */
    private String toProdcd(Tank.FuelType fuelType) {
        return switch (fuelType) {
            case GASOLINE -> "B027";
            case DIESEL   -> "D047";
            case LPG      -> "K015";
            case PREMIUM  -> "B034";
        };
    }

    /** 브랜드 코드 → 브랜드명 변환 */
    private String toBrandName(String code) {
        return switch (code) {
            case "SKE" -> "SK에너지";
            case "GSC" -> "GS칼텍스";
            case "HDO" -> "현대오일뱅크";
            case "SOL" -> "S-OIL";
            case "RTO" -> "농협주유소";
            case "RTX" -> "자가상표";
            case "NHO" -> "농협";
            case "ETC" -> "기타";
            default    -> code;
        };
    }

    @SuppressWarnings("unchecked")
    public List<CompetitorStation> findAround(Tank.FuelType fuelType, int radiusKm) {
        StationInfo myStation = stationInfoMapper.findFirst()
                .orElseThrow(() -> new FuelSyncException("COMP_001", "주유소 위치 정보가 등록되지 않았습니다."));

        if (myStation.getKatechX() == null || myStation.getKatechY() == null) {
            throw new FuelSyncException("COMP_002", "주유소 KATECH X/Y 좌표를 먼저 등록해 주세요.");
        }

        int radiusM = Math.min(Math.max(radiusKm, 1) * 1000, 10000);

        URI uri = UriComponentsBuilder.fromHttpUrl(OPINET_URL)
                .queryParam("code", apiKey)
                .queryParam("x", myStation.getKatechX())
                .queryParam("y", myStation.getKatechY())
                .queryParam("radius", radiusM)
                .queryParam("prodcd", toProdcd(fuelType))
                .queryParam("sort", 1)
                .queryParam("out", "json")
                .build(true)
                .toUri();

        log.info("[Competitor] Opinet request: {}", maskApiKey(uri.toString()));

        try {
            String rawBody = restTemplate.getForObject(uri, String.class);
            if (rawBody == null || rawBody.isBlank()) {
                return Collections.emptyList();
            }
            Map<String, Object> response = objectMapper.readValue(
                    rawBody, new TypeReference<Map<String, Object>>() {});

            List<Map<String, Object>> items = extractItems(response);
            if (items.isEmpty()) {
                return Collections.emptyList();
            }

            return items.stream()
                    .map(this::toCompetitorStation)
                    .filter(Objects::nonNull)
                    .toList();

        } catch (Exception e) {
            // 예외 메시지에 요청 URL(API 키 포함)이 담길 수 있어 스택트레이스 대신 마스킹한 메시지만 남긴다.
            String safeMessage = maskApiKey(e.getMessage());
            log.error("[Competitor] Opinet API call failed: {} ({})", safeMessage, e.getClass().getSimpleName());
            throw new FuelSyncException("COMP_003", "오피넷 API 조회 중 오류가 발생했습니다: " + safeMessage);
        }
    }

    private String maskApiKey(String text) {
        if (text == null || apiKey == null || apiKey.isBlank()) {
            return text;
        }
        return text.replace(apiKey, "***");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractItems(Map<String, Object> root) {
        Map<String, Object> result = asMap(root.get("RESULT"));
        if (result == null) return Collections.emptyList();

        Object oilObj = result.get("OIL");
        if (oilObj instanceof List<?> list) {
            return list.stream()
                    .map(this::asMap)
                    .filter(Objects::nonNull)
                    .toList();
        }
        Map<String, Object> single = asMap(oilObj);
        return single != null ? List.of(single) : Collections.emptyList();
    }

    private CompetitorStation toCompetitorStation(Map<String, Object> item) {
        String brandCode = firstNonBlank(item, "POLL_DIV_CD", "POLL_DIV_CO", "brand");
        if (brandCode == null) {
            brandCode = "ETC";
        }

        BigDecimal price = parseDecimal(item.get("PRICE"), 0);
        BigDecimal distanceRaw = parseDecimal(item.get("DISTANCE"), 2);
        BigDecimal distanceKm = normalizeDistanceKm(distanceRaw);

        return CompetitorStation.builder()
                .stationId(defaultString(firstNonBlank(item, "UNI_ID", "id")))
                .stationName(defaultString(firstNonBlank(item, "OS_NM", "stationName")))
                .brand(brandCode)
                .brandName(toBrandName(brandCode))
                .price(price)
                .distance(distanceKm)
                .address(defaultString(firstNonBlank(item, "NEW_ADR", "VAN_ADR", "address")))
                .build();
    }

    private BigDecimal normalizeDistanceKm(BigDecimal distanceRaw) {
        if (distanceRaw == null) return null;
        if (distanceRaw.compareTo(BigDecimal.valueOf(20)) > 0) {
            return distanceRaw.divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);
        }
        return distanceRaw.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal parseDecimal(Object value, int scale) {
        if (value == null) return null;
        String text = value.toString().trim();
        if (text.isEmpty() || "-".equals(text)) return null;
        try {
            return new BigDecimal(text).setScale(scale, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String firstNonBlank(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                String text = value.toString().trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return null;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object object) {
        if (object instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }
}
