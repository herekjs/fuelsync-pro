package com.fuelsync.domain.stockin.dto;

import com.fuelsync.domain.stockin.model.StockIn;
import jakarta.validation.constraints.*;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class StockInDto {

    public record CreateRequest(
            @NotNull(message = "탱크 ID는 필수입니다.")
            Long tankId,

            @NotNull(message = "입고량(L)은 필수입니다.")
            @DecimalMin(value = "0.01", message = "입고량은 0보다 커야 합니다.")
            BigDecimal inVolume,

            @NotNull(message = "리터당 매입 단가는 필수입니다.")
            @DecimalMin(value = "0.01")
            BigDecimal buyPrice,

            @Size(max = 100)
            String vendorName,

            @NotNull(message = "입고일은 필수입니다.")
            LocalDate inDate,

            @Size(max = 255)
            String memo
    ) {}

    @Builder
    public record Response(
            Long inId,
            Long tankId,
            String tankName,
            String fuelType,
            BigDecimal inVolume,
            BigDecimal buyPrice,
            BigDecimal totalCost,
            String vendorName,
            LocalDate inDate,
            String memo,
            LocalDateTime createdAt
    ) {
        public static Response from(StockIn stockIn) {
            return Response.builder()
                    .inId(stockIn.getInId())
                    .tankId(stockIn.getTankId())
                    .tankName(stockIn.getTankName())
                    .fuelType(stockIn.getFuelType())
                    .inVolume(stockIn.getInVolume())
                    .buyPrice(stockIn.getBuyPrice())
                    .totalCost(stockIn.getInVolume().multiply(stockIn.getBuyPrice()))
                    .vendorName(stockIn.getVendorName())
                    .inDate(stockIn.getInDate())
                    .memo(stockIn.getMemo())
                    .createdAt(stockIn.getCreatedAt())
                    .build();
        }
    }
}
