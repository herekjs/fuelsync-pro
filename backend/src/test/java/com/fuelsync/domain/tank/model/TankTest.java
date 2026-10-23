package com.fuelsync.domain.tank.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TankTest {

    private static Tank tankWithVolume(String volume) {
        return Tank.builder()
                .tankName("T")
                .fuelType(Tank.FuelType.GASOLINE)
                .maxCapacity(new BigDecimal("1000"))
                .currentVolume(new BigDecimal(volume))
                .build();
    }

    @Test
    void create_belowDangerThreshold_isDanger() {
        assertEquals(Tank.TankStatus.DANGER, tankWithVolume("50").getStatus());
    }

    @Test
    void create_belowWarningThreshold_isWarning() {
        assertEquals(Tank.TankStatus.WARNING, tankWithVolume("150").getStatus());
    }

    @Test
    void create_aboveThresholds_isNormal() {
        assertEquals(Tank.TankStatus.NORMAL, tankWithVolume("500").getStatus());
    }

    @Test
    void create_withoutMaxCapacity_staysNormal() {
        Tank tank = Tank.builder().tankName("T").fuelType(Tank.FuelType.DIESEL).build();
        assertEquals(Tank.TankStatus.NORMAL, tank.getStatus());
    }
}
