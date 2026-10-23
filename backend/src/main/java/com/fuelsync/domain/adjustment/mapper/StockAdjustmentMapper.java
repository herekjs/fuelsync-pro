package com.fuelsync.domain.adjustment.mapper;

import com.fuelsync.domain.adjustment.model.StockAdjustment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface StockAdjustmentMapper {

    List<StockAdjustment> findByPeriod(@Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    List<StockAdjustment> findByTankId(@Param("tankId") Long tankId);

    boolean existsByTankIdAndAdjDate(@Param("tankId") Long tankId,
                                     @Param("adjDate") LocalDate adjDate);

    void insert(StockAdjustment stockAdjustment);

    int deleteExcelByTankAndDate(@Param("tankId") Long tankId, @Param("adjDate") LocalDate adjDate);
}
