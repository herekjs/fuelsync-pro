package com.fuelsync.domain.price.service;

import com.fuelsync.common.exception.FuelSyncException;
import com.fuelsync.domain.price.dto.PriceDto;
import com.fuelsync.domain.price.mapper.PriceHistoryMapper;
import com.fuelsync.domain.price.model.PriceHistory;
import com.fuelsync.domain.tank.model.Tank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.fuelsync.common.util.RowValues.toBigDecimal;
import static com.fuelsync.common.util.RowValues.toLocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PriceService {

    private static final Set<Tank.FuelType> SUPPORTED_FUEL_TYPES = EnumSet.of(
            Tank.FuelType.GASOLINE,
            Tank.FuelType.DIESEL
    );

    private final PriceHistoryMapper priceHistoryMapper;

    public List<PriceDto.HistoryResponse> findHistory(
            Tank.FuelType fuelType, LocalDate from, LocalDate to) {
        return priceHistoryMapper.findByFuelTypeAndPeriod(fuelType.name(), from, to)
                .stream()
                .map(PriceDto.HistoryResponse::from)
                .toList();
    }

    public List<PriceDto.TimelineResponse> findTimeline(
            Tank.FuelType fuelType, LocalDate from, LocalDate to) {
        List<Tank.FuelType> targetFuelTypes = resolveFuelTypes(fuelType);
        List<String> fuelTypeNames = targetFuelTypes.stream().map(Enum::name).toList();

        Map<String, BigDecimal> avgBuyPriceByDateAndFuel = toMetricMap(
                priceHistoryMapper.findDailyAvgBuyPriceByFuelTypes(fuelTypeNames, from, to));
        Map<String, BigDecimal> avgActualSellingPriceByDateAndFuel = toMetricMap(
                priceHistoryMapper.findDailyAvgActualSellingPriceByFuelTypes(fuelTypeNames, from, to));

        Map<String, BigDecimal> carriedBuyByFuel = new HashMap<>(
                toFuelTypeOnlyMetricMap(priceHistoryMapper.findLatestAvgBuyPriceBeforeDate(fuelTypeNames, from)));

        List<PriceDto.TimelineResponse> timeline = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            for (String fuelTypeName : fuelTypeNames) {
                String key = metricKey(date, fuelTypeName);
                BigDecimal dayBuy = avgBuyPriceByDateAndFuel.get(key);
                if (dayBuy != null) {
                    carriedBuyByFuel.put(fuelTypeName, dayBuy);
                }
                BigDecimal avgBuyPrice = dayBuy != null ? dayBuy : carriedBuyByFuel.get(fuelTypeName);
                BigDecimal avgActualSellingPrice = avgActualSellingPriceByDateAndFuel.get(key);

                if (avgBuyPrice == null && avgActualSellingPrice == null) {
                    continue;
                }

                timeline.add(PriceDto.TimelineResponse.builder()
                        .date(date)
                        .fuelType(fuelTypeName)
                        .avgBuyPrice(avgBuyPrice)
                        .avgActualSellingPrice(avgActualSellingPrice)
                        .build());
            }
        }
        return timeline;
    }

    /**
     * 권장 판매가 시뮬레이션
     * suggestedPrice = avgBuyPrice / (1 - targetMarginRate/100)
     */
    public PriceDto.SimulateResponse simulate(PriceDto.SimulateRequest request) {
        BigDecimal marginFactor = BigDecimal.ONE.subtract(
                request.targetMarginRate().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        BigDecimal suggestedPrice = request.avgBuyPrice()
                .divide(marginFactor, 2, RoundingMode.HALF_UP);

        BigDecimal priceDiff = request.competitorAvgPrice() != null
                ? suggestedPrice.subtract(request.competitorAvgPrice()).setScale(2, RoundingMode.HALF_UP)
                : null;

        return PriceDto.SimulateResponse.builder()
                .fuelType(request.fuelType().name())
                .avgBuyPrice(request.avgBuyPrice())
                .targetMarginRate(request.targetMarginRate())
                .suggestedPrice(suggestedPrice)
                .competitorAvgPrice(request.competitorAvgPrice())
                .priceDiffFromCompetitor(priceDiff)
                .build();
    }

    @Transactional
    public PriceDto.HistoryResponse record(PriceDto.RecordRequest request) {
        boolean exists = priceHistoryMapper.existsByFuelTypeAndOpinetDate(
                request.fuelType().name(), request.opinetDate());
        if (exists) {
            throw new FuelSyncException(FuelSyncException.Price.DUPLICATE,
                    "해당 날짜에 동일 유종의 가격 기록이 이미 존재합니다.");
        }

        PriceHistory ph = PriceHistory.builder()
                .fuelType(request.fuelType())
                .ourPrice(request.ourPrice())
                .competitorAvgPrice(request.competitorAvgPrice())
                .opinetDate(request.opinetDate())
                .build();

        priceHistoryMapper.insert(ph);
        log.info("[Price] recorded fuelType={}, ourPrice={}, date={}", request.fuelType(), request.ourPrice(), request.opinetDate());
        return PriceDto.HistoryResponse.from(ph);
    }

    private List<Tank.FuelType> resolveFuelTypes(Tank.FuelType fuelType) {
        if (fuelType == null) {
            return List.of(Tank.FuelType.GASOLINE, Tank.FuelType.DIESEL);
        }
        if (!SUPPORTED_FUEL_TYPES.contains(fuelType)) {
            throw new FuelSyncException(FuelSyncException.Price.NOT_FOUND, "지원하지 않는 유종입니다.");
        }
        return List.of(fuelType);
    }

    private Map<String, BigDecimal> toMetricMap(List<Map<String, Object>> rows) {
        Map<String, BigDecimal> metricMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            LocalDate metricDate = toLocalDate(row.get("metricDate"));
            String fuelType = row.get("fuelType") != null ? row.get("fuelType").toString() : null;
            BigDecimal metricValue = toBigDecimal(row.get("metricValue"));

            if (metricDate == null || fuelType == null || metricValue == null) {
                continue;
            }
            metricMap.put(metricKey(metricDate, fuelType), metricValue.setScale(2, RoundingMode.HALF_UP));
        }
        return metricMap;
    }

    /** fuelType 문자열 키 → metricValue (일자 없음) */
    private Map<String, BigDecimal> toFuelTypeOnlyMetricMap(List<Map<String, Object>> rows) {
        Map<String, BigDecimal> metricMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String fuelType = row.get("fuelType") != null ? row.get("fuelType").toString() : null;
            BigDecimal metricValue = toBigDecimal(row.get("metricValue"));
            if (fuelType == null || metricValue == null) {
                continue;
            }
            metricMap.put(fuelType, metricValue.setScale(2, RoundingMode.HALF_UP));
        }
        return metricMap;
    }

    private String metricKey(LocalDate date, String fuelType) {
        return date + "|" + fuelType;
    }
}
