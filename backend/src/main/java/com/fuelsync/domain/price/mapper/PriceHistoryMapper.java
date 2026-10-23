package com.fuelsync.domain.price.mapper;

import com.fuelsync.domain.price.model.PriceHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface PriceHistoryMapper {

    List<PriceHistory> findByFuelTypeAndPeriod(@Param("fuelType") String fuelType,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    boolean existsByFuelTypeAndOpinetDate(@Param("fuelType") String fuelType,
                                         @Param("opinetDate") LocalDate opinetDate);

    List<Map<String, Object>> findDailyAvgBuyPriceByFuelTypes(@Param("fuelTypes") List<String> fuelTypes,
                                                               @Param("from") LocalDate from,
                                                               @Param("to") LocalDate to);

    List<Map<String, Object>> findDailyAvgActualSellingPriceByFuelTypes(@Param("fuelTypes") List<String> fuelTypes,
                                                                         @Param("from") LocalDate from,
                                                                         @Param("to") LocalDate to);

    /**
     * 각 유종별로 {@code beforeDate} 미만 입고일 중 최종 일자의 일별 평균 매입단가(해당 일 입고 건 AVG).
     * 조회 구간 시작일 이전까지의 재고단가 이월 시드로 사용.
     */
    List<Map<String, Object>> findLatestAvgBuyPriceBeforeDate(@Param("fuelTypes") List<String> fuelTypes,
                                                             @Param("beforeDate") LocalDate beforeDate);

    void insert(PriceHistory priceHistory);

    void upsertFromExcel(@Param("fuelType") String fuelType,
                         @Param("ourPrice") BigDecimal ourPrice,
                         @Param("opinetDate") LocalDate opinetDate);

    /** 가장 최근의 자체 판매가(our_price), 해당 일자 이하의 opinet_date 중 최대 */
    BigDecimal findLatestOurPriceByFuelTypeOnOrBefore(@Param("fuelType") String fuelType,
                                                      @Param("asOfDate") LocalDate asOfDate);
}
