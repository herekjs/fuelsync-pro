package com.fuelsync.domain.tank.dto;

import com.fuelsync.domain.tank.model.Tank;
import jakarta.validation.constraints.*;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class TankDto {

    // ─── Request ───────────────────────────────────────────────────────────────

    public record CreateRequest(
            @NotBlank(message = "탱크 이름은 필수입니다.")
            @Size(max = 30)
            String tankName,

            @NotNull(message = "유종은 필수입니다.")
            Tank.FuelType fuelType,

            @NotNull(message = "최대 용량은 필수입니다.")
            @DecimalMin(value = "0.01", message = "최대 용량은 0보다 커야 합니다.")
            BigDecimal maxCapacity,

            BigDecimal initialVolume,

            @Min(value = 1) @Max(value = 100)
            Integer thresholdWarning,

            @Min(value = 1) @Max(value = 100)
            Integer thresholdDanger
    ) {}

    public record SensorUpdateRequest(
            BigDecimal temperature,
            BigDecimal waterLevel
    ) {}

    public record InventoryUpdateRequest(
            @NotNull(message = "최대 용량은 필수입니다.")
            @DecimalMin(value = "0.01", message = "최대 용량은 0보다 커야 합니다.")
            BigDecimal maxCapacity,

            @NotNull(message = "현재 재고는 필수입니다.")
            @DecimalMin(value = "0", message = "현재 재고는 0 이상이어야 합니다.")
            BigDecimal currentVolume
    ) {}

    @Builder
    public record DailyRemainingResponse(
            LocalDate date,
            BigDecimal inVolume,
            BigDecimal soldVolume,
            BigDecimal adjustmentVolume,
            BigDecimal endVolume
    ) {}

    // ─── Response ──────────────────────────────────────────────────────────────

    @Builder
    public record Response(
            Long tankId,
            String tankName,
            String fuelType,
            BigDecimal maxCapacity,
            BigDecimal currentVolume,
            BigDecimal fillRatio,
            BigDecimal temperature,
            BigDecimal waterLevel,
            Integer thresholdWarning,
            Integer thresholdDanger,
            String status,
            LocalDateTime updatedAt
    ) {
        public static Response from(Tank tank) {
            return Response.builder()
                    .tankId(tank.getTankId())
                    .tankName(tank.getTankName())
                    .fuelType(tank.getFuelType().name())
                    .maxCapacity(tank.getMaxCapacity())
                    .currentVolume(tank.getCurrentVolume())
                    .fillRatio(tank.getFillRatio())
                    .temperature(tank.getTemperature())
                    .waterLevel(tank.getWaterLevel())
                    .thresholdWarning(tank.getThresholdWarning())
                    .thresholdDanger(tank.getThresholdDanger())
                    .status(tank.getStatus().name())
                    .updatedAt(tank.getUpdatedAt())
                    .build();
        }
    }
}
