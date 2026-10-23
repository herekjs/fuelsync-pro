package com.fuelsync.domain.price.mapper;

import com.fuelsync.domain.price.model.SellingPrice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface SellingPriceMapper {

    List<SellingPrice> findLatestAll();

    Optional<SellingPrice> findLatestByFuelType(@Param("fuelType") String fuelType);

    void upsert(SellingPrice sellingPrice);
}
