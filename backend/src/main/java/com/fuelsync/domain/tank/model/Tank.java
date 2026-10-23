package com.fuelsync.domain.tank.model;

import com.fuelsync.common.exception.FuelSyncException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class Tank {

    private Long tankId;
    private String tankName;
    private FuelType fuelType;
    private BigDecimal maxCapacity;
    private BigDecimal currentVolume;
    private BigDecimal temperature;
    private BigDecimal waterLevel;
    private Integer thresholdWarning;
    private Integer thresholdDanger;
    private TankStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Tank(String tankName, FuelType fuelType, BigDecimal maxCapacity,
                BigDecimal currentVolume, Integer thresholdWarning, Integer thresholdDanger) {
        this.tankName = tankName;
        this.fuelType = fuelType;
        this.maxCapacity = maxCapacity;
        this.currentVolume = currentVolume != null ? currentVolume : BigDecimal.ZERO;
        this.thresholdWarning = thresholdWarning != null ? thresholdWarning : 20;
        this.thresholdDanger = thresholdDanger != null ? thresholdDanger : 10;
        this.waterLevel = BigDecimal.ZERO;
        this.status = TankStatus.NORMAL;
    }

    public static TankBuilder builder() {
        return new TankBuilder();
    }

    public static class TankBuilder {
        private String tankName;
        private FuelType fuelType;
        private BigDecimal maxCapacity;
        private BigDecimal currentVolume;
        private Integer thresholdWarning;
        private Integer thresholdDanger;

        public TankBuilder tankName(String tankName) { this.tankName = tankName; return this; }
        public TankBuilder fuelType(FuelType fuelType) { this.fuelType = fuelType; return this; }
        public TankBuilder maxCapacity(BigDecimal maxCapacity) { this.maxCapacity = maxCapacity; return this; }
        public TankBuilder currentVolume(BigDecimal currentVolume) { this.currentVolume = currentVolume; return this; }
        public TankBuilder thresholdWarning(Integer thresholdWarning) { this.thresholdWarning = thresholdWarning; return this; }
        public TankBuilder thresholdDanger(Integer thresholdDanger) { this.thresholdDanger = thresholdDanger; return this; }

        public Tank build() {
            return new Tank(tankName, fuelType, maxCapacity, currentVolume, thresholdWarning, thresholdDanger);
        }
    }

    /**
     * 판매 후 잔량 차감 및 상태 재산정
     */
    public void deductVolume(BigDecimal liter) {
        if (currentVolume.compareTo(liter) < 0) {
            throw new FuelSyncException(
                    FuelSyncException.Tank.INSUFFICIENT,
                    "잔량이 부족합니다. 현재 잔량: " + currentVolume + "L, 요청: " + liter + "L"
            );
        }
        this.currentVolume = this.currentVolume.subtract(liter).setScale(2, RoundingMode.HALF_UP);
        recalculateStatus();
    }

    /**
     * 입고 후 잔량 추가 및 상태 재산정
     */
    public void addVolume(BigDecimal liter) {
        BigDecimal newVolume = this.currentVolume.add(liter).setScale(2, RoundingMode.HALF_UP);
        if (newVolume.compareTo(maxCapacity) > 0) {
            throw new FuelSyncException(
                    FuelSyncException.Tank.OVERFLOW,
                    "탱크 최대 용량을 초과합니다. 최대: " + maxCapacity + "L, 요청 후: " + newVolume + "L"
            );
        }
        this.currentVolume = newVolume;
        recalculateStatus();
    }

    public void updateSensorData(BigDecimal temperature, BigDecimal waterLevel) {
        this.temperature = temperature;
        this.waterLevel = waterLevel;
    }

    /**
     * 정보관리 등에서 최대 용량·현재 재고를 직접 설정할 때 사용. 잔량 대비 상태 재산정 포함.
     */
    public void updateCapacityAndVolume(BigDecimal maxCapacity, BigDecimal currentVolume) {
        if (maxCapacity == null || maxCapacity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new FuelSyncException(
                    FuelSyncException.Tank.INVALID_INVENTORY,
                    "최대 용량은 0보다 커야 합니다."
            );
        }
        if (currentVolume == null || currentVolume.compareTo(BigDecimal.ZERO) < 0) {
            throw new FuelSyncException(
                    FuelSyncException.Tank.INVALID_INVENTORY,
                    "현재 재고는 0 이상이어야 합니다."
            );
        }
        if (currentVolume.compareTo(maxCapacity) > 0) {
            throw new FuelSyncException(
                    FuelSyncException.Tank.OVERFLOW,
                    "현재 재고가 최대 용량을 초과할 수 없습니다. 최대: " + maxCapacity + "L"
            );
        }
        this.maxCapacity = maxCapacity.setScale(2, RoundingMode.HALF_UP);
        this.currentVolume = currentVolume.setScale(2, RoundingMode.HALF_UP);
        recalculateStatus();
    }

    public BigDecimal getFillRatio() {
        if (maxCapacity == null || maxCapacity.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return currentVolume.divide(maxCapacity, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void recalculateStatus() {
        BigDecimal ratio = getFillRatio();
        if (ratio.compareTo(BigDecimal.valueOf(thresholdDanger)) <= 0) {
            this.status = TankStatus.DANGER;
        } else if (ratio.compareTo(BigDecimal.valueOf(thresholdWarning)) <= 0) {
            this.status = TankStatus.WARNING;
        } else {
            this.status = TankStatus.NORMAL;
        }
    }

    public enum FuelType { GASOLINE, DIESEL, LPG, PREMIUM }
    public enum TankStatus { NORMAL, WARNING, DANGER }
}
