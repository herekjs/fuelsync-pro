package com.fuelsync.domain.price.dto;

import com.fuelsync.domain.price.model.PriceHistory;
import com.fuelsync.domain.tank.model.Tank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PriceDto {

    public record RecordRequest(
            @NotNull Tank.FuelType fuelType,
            @NotNull @DecimalMin("0.01") BigDecimal ourPrice,
            BigDecimal competitorAvgPrice,
            @NotNull LocalDate opinetDate
    ) {}

    public record SimulateRequest(
            @NotNull Tank.FuelType fuelType,
            @NotNull @DecimalMin("0.01") BigDecimal avgBuyPrice,
            @NotNull @DecimalMin("0.01") BigDecimal targetMarginRate,
            BigDecimal competitorAvgPrice
    ) {}

    @Builder
    public record SimulateResponse(
            String fuelType,
            BigDecimal avgBuyPrice,
            BigDecimal targetMarginRate,
            BigDecimal suggestedPrice,
            BigDecimal competitorAvgPrice,
            BigDecimal priceDiffFromCompetitor
    ) {}

    @Builder
    public record HistoryResponse(
            Long historyId,
            String fuelType,
            BigDecimal ourPrice,
            BigDecimal competitorAvgPrice,
            LocalDate opinetDate
    ) {
        public static HistoryResponse from(PriceHistory ph) {
            return HistoryResponse.builder()
                    .historyId(ph.getHistoryId())
                    .fuelType(ph.getFuelType().name())
                    .ourPrice(ph.getOurPrice())
                    .competitorAvgPrice(ph.getCompetitorAvgPrice())
                    .opinetDate(ph.getOpinetDate())
                    .build();
        }
    }

    @Builder
    public record TimelineResponse(
            LocalDate date,
            String fuelType,
            BigDecimal avgBuyPrice,
            BigDecimal avgActualSellingPrice
    ) {}
}
