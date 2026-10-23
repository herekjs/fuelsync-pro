package com.fuelsync.domain.tank.mapper;

import com.fuelsync.domain.tank.model.Tank;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mapper
public interface TankMapper {

    List<Tank> findAll();

    List<Tank> findByFuelType(@Param("fuelType") String fuelType);

    List<Tank> findAttention();

    Optional<Tank> findById(@Param("tankId") Long tankId);

    void insert(Tank tank);

    void updateVolume(Tank tank);

    void updateCapacityAndVolume(Tank tank);

    void updateSensor(@Param("tankId") Long tankId,
                      @Param("temperature") BigDecimal temperature,
                      @Param("waterLevel") BigDecimal waterLevel);

    List<Map<String, Object>> findDailyStockInVolumeByTankId(@Param("tankId") Long tankId,
                                                              @Param("from") String from,
                                                              @Param("to") String to);

    List<Map<String, Object>> findDailySoldVolumeByTankId(@Param("tankId") Long tankId,
                                                           @Param("from") String from,
                                                           @Param("to") String to);

    List<Map<String, Object>> findDailyAdjustmentVolumeByTankId(@Param("tankId") Long tankId,
                                                                 @Param("from") String from,
                                                                 @Param("to") String to);

    void upsertDailyLevel(@Param("tankId") Long tankId,
                          @Param("levelDate") String levelDate,
                          @Param("endVolumeLiters") BigDecimal endVolumeLiters,
                          @Param("source") String source);

    List<Map<String, Object>> findDailyLevelsByTankAndDateRange(@Param("tankId") Long tankId,
                                                                 @Param("from") String from,
                                                                 @Param("to") String to);

    BigDecimal findLatestDailyLevelEndVolume(@Param("tankId") Long tankId);

    BigDecimal findLatestDailyLevelEndVolumeBefore(@Param("tankId") Long tankId,
                                                   @Param("beforeDate") String beforeDate);

    int deleteExcelDailyLevelByTankAndDate(@Param("tankId") Long tankId,
                                           @Param("levelDate") String levelDate);
}
