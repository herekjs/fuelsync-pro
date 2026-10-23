package com.fuelsync.domain.dashboard.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class DashboardDto {

    @Builder
    public record Response(
            List<TankSummary> tanks,
            TodaySummary today,
            List<FuelTypeSummary> fuelTypeSummaries
    ) {}

    @Builder
    public record TankSummary(
            Long tankId,
            String tankName,
            String fuelType,
            BigDecimal maxCapacity,
            BigDecimal currentVolume,
            BigDecimal fillRatio,
            BigDecimal temperature,
            BigDecimal waterLevel,
            String status,
            // 최근 입고일 이후 일평균 판매 기준 소진 예상 일수
            Double stockRunwayDays,
            /** 소진예상(일) 산출 기준일 = 해당 탱크 최근 입고일(in_date) */
            LocalDate stockRunwayBaselineDate,
            /** 기준일 0시 이후 판매 데이터 중 마지막 판매일 */
            LocalDate lastSaleDate,
            // 오늘 해당 탱크 판매량 (L)
            BigDecimal todaySoldLiter,
            // 오늘 해당 탱크 매출액 (원)
            BigDecimal todayRevenue
    ) {}

    @Builder
    public record TodaySummary(
            BigDecimal totalRevenue,
            BigDecimal totalSoldLiter,
            BigDecimal estimatedMargin,
            int tankCount,
            int warningCount,
            int dangerCount
    ) {}

    @Builder
    public record FuelTypeSummary(
            String fuelType,
            BigDecimal totalVolume,
            BigDecimal totalCapacity,
            BigDecimal fillRatio,
            int tankCount
    ) {}
}
