package com.fuelsync.domain.station.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class StationInfo {

    private Long stationId;
    private String stationName;
    private String address;
    private BigDecimal katechX;
    private BigDecimal katechY;
    private String phone;
    private String brand;
    private LocalDateTime updatedAt;
}
