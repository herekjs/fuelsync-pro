package com.fuelsync.domain.stockin.model;

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
public class StockIn {

    private Long inId;
    private Long tankId;
    private String tankName;
    private String fuelType;
    private BigDecimal inVolume;
    private BigDecimal buyPrice;
    private String vendorName;
    private LocalDate inDate;
    private String memo;
    /** MANUAL | EXCEL */
    private String inputType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
