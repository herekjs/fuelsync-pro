package com.fuelsync.domain.adjustment.dto;

import com.fuelsync.domain.adjustment.model.StockAdjustment;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class AdjustmentDto {

    public record CreateRequest(
            @NotNull Long tankId,

            @NotNull(message = "장부 재고(L)는 필수입니다.")
            @DecimalMin("0")
            BigDecimal bookStock,

            @NotNull(message = "실측 재고(L)는 필수입니다.")
            @DecimalMin("0")
            BigDecimal physicalStock,

            @NotNull LocalDate adjDate,

            String memo
    ) {}

    @Builder
    public record Response(
            Long adjId,
            Long tankId,
            String tankName,
            String fuelType,
            BigDecimal bookStock,
            BigDecimal physicalStock,
            BigDecimal diffVolume,
            BigDecimal diffAmount,
            LocalDate adjDate,
            String memo,
            StockAdjustment.InputType inputType,
            LocalDateTime createdAt
    ) {
        public static Response from(StockAdjustment adj) {
            StockAdjustment.InputType it = adj.getInputType() != null ? adj.getInputType() : StockAdjustment.InputType.WEB;
            return Response.builder()
                    .adjId(adj.getAdjId())
                    .tankId(adj.getTankId())
                    .tankName(adj.getTankName())
                    .fuelType(adj.getFuelType())
                    .bookStock(adj.getBookStock())
                    .physicalStock(adj.getPhysicalStock())
                    .diffVolume(adj.getDiffVolume())
                    .diffAmount(adj.getDiffAmount())
                    .adjDate(adj.getAdjDate())
                    .memo(adj.getMemo())
                    .inputType(it)
                    .createdAt(adj.getCreatedAt())
                    .build();
        }
    }
}
