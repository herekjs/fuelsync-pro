package com.fuelsync.domain.competitor.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class CompetitorStation {

    private String stationId;    // 오피넷 주유소 ID (UNI_ID)
    private String stationName;  // 상호명
    private String brand;        // 브랜드 (SKE, GSC, HDO, SOL, RTO, RTX, NHO, ETC)
    private String brandName;    // 브랜드명
    private BigDecimal price;    // 해당 유종 판매가 (원/L)
    private BigDecimal distance; // 거리 (km)
    private String address;      // 주소
}
