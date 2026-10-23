package com.fuelsync.domain.adjustment.mapper;

import com.fuelsync.domain.adjustment.model.StockAdjustment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Mapper
public interface StockAdjustmentMapper {

    List<StockAdjustment> findByPeriod(@Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    List<StockAdjustment> findByTankId(@Param("tankId") Long tankId);

    /** 엑셀 덮어쓰기: 엑셀이 아닌(웹) 재고 조정만 있는지. 엑셀 조정은 덮어쓰며 지워지므로 제외한다. */
    boolean existsNonExcelByTankIdAndAdjDate(@Param("tankId") Long tankId,
                                             @Param("adjDate") LocalDate adjDate);

    boolean existsByTankIdAndAdjDate(@Param("tankId") Long tankId,
                                     @Param("adjDate") LocalDate adjDate);

    void insert(StockAdjustment stockAdjustment);

    int deleteExcelByTankAndDate(@Param("tankId") Long tankId, @Param("adjDate") LocalDate adjDate);

    /** 덮어쓰기: 지워질 EXCEL 재고 조정 차이(physical − book) 합계 (잔량에 이미 반영된 양) */
    BigDecimal sumExcelDiffByTankAndDates(@Param("tankId") Long tankId,
                                          @Param("dates") Collection<LocalDate> dates);
}
