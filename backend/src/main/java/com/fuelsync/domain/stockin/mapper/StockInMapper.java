package com.fuelsync.domain.stockin.mapper;

import com.fuelsync.domain.stockin.model.StockIn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Mapper
public interface StockInMapper {

    List<StockIn> findByTankId(@Param("tankId") Long tankId);

    List<StockIn> findByPeriod(@Param("from") LocalDate from,
                               @Param("to") LocalDate to);

    BigDecimal findAvgBuyPriceSince(@Param("tankId") Long tankId,
                                   @Param("since") LocalDate since);

    BigDecimal findOverallAvgBuyPriceSince(@Param("since") LocalDate since);

    /** 탱크별 가장 최근 입고일 (없으면 null) */
    LocalDate findLatestInDateByTankId(@Param("tankId") Long tankId);

    void insert(StockIn stockIn);

    int deleteExcelByTankAndDate(@Param("tankId") Long tankId,
                                 @Param("inDate") LocalDate inDate);

    /** 증분 업로드: 해당 탱크·일자에 EXCEL 입고가 이미 있는지 */
    int countExcelByTankAndDate(@Param("tankId") Long tankId, @Param("inDate") LocalDate inDate);

    /** 덮어쓰기: 지워질 EXCEL 입고량 합계 (잔량에 이미 더해진 양) */
    BigDecimal sumExcelVolumeByTankAndDates(@Param("tankId") Long tankId,
                                            @Param("dates") Collection<LocalDate> dates);
}
