package com.fuelsync.domain.transaction.mapper;

import com.fuelsync.domain.transaction.model.Transaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface TransactionMapper {

    List<Transaction> findByPeriod(@Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to);

    List<Transaction> findRecent(@Param("since") LocalDateTime since);

    Map<String, Object> findTodaySummaryByTank(@Param("tankId") Long tankId,
                                               @Param("startOfDay") LocalDateTime startOfDay);

    List<Map<String, Object>> findHourlyLiterSince(@Param("tankId") Long tankId,
                                                   @Param("since") LocalDateTime since);

    BigDecimal sumLiterSince(@Param("tankId") Long tankId,
                            @Param("since") LocalDateTime since);

    /** 기준 시각 이후 거래 중 가장 늦은 판매일(날짜만); 없으면 null */
    LocalDate findLastSaleDateSince(@Param("tankId") Long tankId,
                                   @Param("since") LocalDateTime since);

    void insert(Transaction transaction);

    void insertBatch(@Param("list") List<Transaction> list);

    List<Map<String, Object>> findMonthlyStats(@Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);

    List<Map<String, Object>> findDailyStats(@Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate);

    /** ISO 주: 해당 주의 월요일 날짜를 period_key로 집계 */
    List<Map<String, Object>> findWeeklyStats(@Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate);

    /** 요일(WEEKDAY 0=월~6=일)별 일평균 판매량(L), 유종별 */
    List<Map<String, Object>> findDayOfWeekAvgStats(@Param("startDate") LocalDate startDate,
                                                    @Param("endDate") LocalDate endDate);

    /** 엑셀 업로드로 저장된 해당 탱크·일자 판매만 삭제 (덮어쓰기용) */
    int deleteExcelByTankAndDate(@Param("tankId") Long tankId, @Param("saleDate") LocalDate saleDate);

    /** 증분 업로드: 해당 탱크·일자에 EXCEL 판매가 이미 있는지 */
    int countExcelByTankAndDate(@Param("tankId") Long tankId, @Param("saleDate") LocalDate saleDate);
}
