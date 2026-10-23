package com.fuelsync.domain.price.model;

import com.fuelsync.domain.tank.model.Tank;
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
public class PriceHistory {

    private Long historyId;
    private Tank.FuelType fuelType;
    private BigDecimal ourPrice;
    private BigDecimal competitorAvgPrice;
    private LocalDate opinetDate;
    private LocalDateTime createdAt;
}
