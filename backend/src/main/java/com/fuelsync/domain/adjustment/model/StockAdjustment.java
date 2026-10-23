package com.fuelsync.domain.adjustment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAdjustment {

    public enum InputType {
        WEB, EXCEL
    }

    private Long adjId;
    private Long tankId;
    private String tankName;
    private String fuelType;
    private BigDecimal bookStock;
    private BigDecimal physicalStock;
    private BigDecimal diffVolume;
    private BigDecimal diffAmount;
    private LocalDate adjDate;
    private String memo;
    private InputType inputType;
    private LocalDateTime createdAt;
}
