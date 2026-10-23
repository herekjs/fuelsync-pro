package com.fuelsync.domain.price.service;

import com.fuelsync.domain.price.mapper.SellingPriceMapper;
import com.fuelsync.domain.price.model.SellingPrice;
import com.fuelsync.domain.tank.model.Tank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellingPriceService {

    private final SellingPriceMapper sellingPriceMapper;

    public List<SellingPrice> findAll() {
        return sellingPriceMapper.findLatestAll();
    }

    @Transactional
    public SellingPrice upsert(Tank.FuelType fuelType, BigDecimal price, LocalDate effectiveFrom) {
        SellingPrice sp = new SellingPrice();
        sp.setFuelType(fuelType);
        sp.setSellingPrice(price);
        sp.setEffectiveFrom(effectiveFrom != null ? effectiveFrom : LocalDate.now());
        sellingPriceMapper.upsert(sp);
        log.info("[SellingPrice] upserted fuelType={}, price={}", fuelType, price);
        return sp;
    }
}
