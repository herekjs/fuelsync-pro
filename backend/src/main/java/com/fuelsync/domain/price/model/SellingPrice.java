package com.fuelsync.domain.price.model;

import com.fuelsync.domain.tank.model.Tank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class SellingPrice {

    private Long priceId;
    private Tank.FuelType fuelType;
    private BigDecimal sellingPrice;
    private LocalDate effectiveFrom;
    private LocalDateTime createdAt;
}
