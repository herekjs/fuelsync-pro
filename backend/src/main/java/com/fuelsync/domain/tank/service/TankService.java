package com.fuelsync.domain.tank.service;

import com.fuelsync.common.exception.FuelSyncException;
import com.fuelsync.domain.tank.dto.TankDto;
import com.fuelsync.domain.tank.mapper.TankMapper;
import com.fuelsync.domain.tank.model.Tank;
import com.fuelsync.websocket.StockEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fuelsync.common.util.RowValues.toBigDecimal;
import static com.fuelsync.common.util.RowValues.toLocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TankService {

    private final TankMapper tankMapper;
    private final StockEventPublisher stockEventPublisher;

    public List<TankDto.Response> findAll() {
        return tankMapper.findAll()
                .stream()
                .map(TankDto.Response::from)
                .toList();
    }

    public TankDto.Response findById(Long tankId) {
        return TankDto.Response.from(getTankOrThrow(tankId));
    }

    public List<TankDto.Response> findByFuelType(Tank.FuelType fuelType) {
        return tankMapper.findByFuelType(fuelType.name())
                .stream()
                .map(TankDto.Response::from)
                .toList();
    }

    public List<TankDto.Response> findAllRequiringAttention() {
        return tankMapper.findAttention()
                .stream()
                .map(TankDto.Response::from)
                .toList();
    }

    public List<TankDto.DailyRemainingResponse> findDailyRemaining(Long tankId, LocalDate from, LocalDate to) {
        Tank tank = getTankOrThrow(tankId);
        LocalDate today = LocalDate.now();
        LocalDate endDate = to.isAfter(today) ? today : to;

        if (from.isAfter(endDate)) {
            return List.of();
        }

        List<Map<String, Object>> stockInRows = tankMapper.findDailyStockInVolumeByTankId(
                tankId, from.toString(), today.toString());
        List<Map<String, Object>> soldRows = tankMapper.findDailySoldVolumeByTankId(
                tankId, from.toString(), today.toString());
        List<Map<String, Object>> adjustmentRows = tankMapper.findDailyAdjustmentVolumeByTankId(
                tankId, from.toString(), today.toString());
        List<Map<String, Object>> dailyLevelRows = tankMapper.findDailyLevelsByTankAndDateRange(
                tankId, from.toString(), endDate.toString());

        Map<LocalDate, BigDecimal> inVolumeByDate = toMetricMap(stockInRows);
        Map<LocalDate, BigDecimal> soldVolumeByDate = toMetricMap(soldRows);
        Map<LocalDate, BigDecimal> adjustmentVolumeByDate = toMetricMap(adjustmentRows);
        Map<LocalDate, BigDecimal> levelByDate = toMetricMap(dailyLevelRows);

        // End-of-day volume before `from`: prefer latest snapshot, else back-calc from current volume.
        BigDecimal carry = tankMapper.findLatestDailyLevelEndVolumeBefore(tankId, from.toString());
        if (carry == null) {
            carry = tank.getCurrentVolume().setScale(2, RoundingMode.HALF_UP);
            for (LocalDate d = today; d.isAfter(from.minusDays(1)); d = d.minusDays(1)) {
                carry = carry.subtract(dailyNetMovement(inVolumeByDate, soldVolumeByDate, adjustmentVolumeByDate, d))
                        .setScale(2, RoundingMode.HALF_UP);
            }
        } else {
            carry = carry.setScale(2, RoundingMode.HALF_UP);
        }

        List<TankDto.DailyRemainingResponse> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(endDate); d = d.plusDays(1)) {
            BigDecimal inVolume = inVolumeByDate.getOrDefault(d, BigDecimal.ZERO);
            BigDecimal soldVolume = soldVolumeByDate.getOrDefault(d, BigDecimal.ZERO);
            BigDecimal adjustmentVolume = adjustmentVolumeByDate.getOrDefault(d, BigDecimal.ZERO);
            BigDecimal netMovement = dailyNetMovement(inVolumeByDate, soldVolumeByDate, adjustmentVolumeByDate, d);

            BigDecimal closing;
            if (levelByDate.containsKey(d)) {
                closing = levelByDate.get(d).setScale(2, RoundingMode.HALF_UP);
            } else {
                // No snapshot: carry forward from previous end-of-day (chain from latest snapshot / seed).
                closing = carry.add(netMovement).setScale(2, RoundingMode.HALF_UP);
            }

            result.add(TankDto.DailyRemainingResponse.builder()
                    .date(d)
                    .inVolume(inVolume.setScale(2, RoundingMode.HALF_UP))
                    .soldVolume(soldVolume.setScale(2, RoundingMode.HALF_UP))
                    .adjustmentVolume(adjustmentVolume.setScale(2, RoundingMode.HALF_UP))
                    .endVolume(closing)
                    .build());

            carry = closing;
        }

        return result;
    }

    private static BigDecimal dailyNetMovement(Map<LocalDate, BigDecimal> inVolumeByDate,
                                                 Map<LocalDate, BigDecimal> soldVolumeByDate,
                                                 Map<LocalDate, BigDecimal> adjustmentVolumeByDate,
                                                 LocalDate d) {
        BigDecimal inVolume = inVolumeByDate.getOrDefault(d, BigDecimal.ZERO);
        BigDecimal soldVolume = soldVolumeByDate.getOrDefault(d, BigDecimal.ZERO);
        BigDecimal adjustmentVolume = adjustmentVolumeByDate.getOrDefault(d, BigDecimal.ZERO);
        return inVolume.subtract(soldVolume).add(adjustmentVolume);
    }

    @Transactional
    public TankDto.Response create(TankDto.CreateRequest request) {
        Tank tank = Tank.builder()
                .tankName(request.tankName())
                .fuelType(request.fuelType())
                .maxCapacity(request.maxCapacity())
                .currentVolume(request.initialVolume())
                .thresholdWarning(request.thresholdWarning())
                .thresholdDanger(request.thresholdDanger())
                .build();
        tankMapper.insert(tank);
        log.info("[Tank] create tankId={}, tankName={}, fuelType={}", tank.getTankId(), tank.getTankName(), tank.getFuelType());
        return TankDto.Response.from(tank);
    }

    @Transactional
    public TankDto.Response updateSensor(Long tankId, TankDto.SensorUpdateRequest request) {
        Tank tank = getTankOrThrow(tankId);
        tank.updateSensorData(request.temperature(), request.waterLevel());
        tankMapper.updateSensor(tankId, request.temperature(), request.waterLevel());
        return TankDto.Response.from(tank);
    }

    @Transactional
    public TankDto.Response updateCapacityAndVolume(Long tankId, TankDto.InventoryUpdateRequest request) {
        Tank tank = getTankForUpdateOrThrow(tankId);
        tank.updateCapacityAndVolume(request.maxCapacity(), request.currentVolume());
        tankMapper.updateCapacityAndVolume(tank);
        log.info("[Tank] inventory update tankId={}, maxCapacity={}L, currentVolume={}L, status={}",
                tankId, tank.getMaxCapacity(), tank.getCurrentVolume(), tank.getStatus());

        stockEventPublisher.publishTankChange(tank);
        return TankDto.Response.from(tank);
    }

    public Tank getTankOrThrow(Long tankId) {
        return tankMapper.findById(tankId)
                .orElseThrow(() -> new FuelSyncException(
                        FuelSyncException.Tank.NOT_FOUND,
                        "탱크를 찾을 수 없습니다. tankId=" + tankId));
    }

    /** 잔량 변경용 조회. 호출자의 쓰기 트랜잭션 안에서만 사용할 수 있으며 커밋/롤백까지 행 잠금이 유지된다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Tank getTankForUpdateOrThrow(Long tankId) {
        return tankMapper.findByIdForUpdate(tankId)
                .orElseThrow(() -> new FuelSyncException(
                        FuelSyncException.Tank.NOT_FOUND,
                        "탱크를 찾을 수 없습니다. tankId=" + tankId));
    }

    private Map<LocalDate, BigDecimal> toMetricMap(List<Map<String, Object>> rows) {
        Map<LocalDate, BigDecimal> metricByDate = new HashMap<>();
        for (Map<String, Object> row : rows) {
            LocalDate metricDate = toLocalDate(row.get("metricDate"));
            BigDecimal metricValue = toBigDecimal(row.get("metricValue"));
            if (metricDate == null || metricValue == null) {
                continue;
            }
            metricByDate.put(metricDate, metricValue);
        }
        return metricByDate;
    }

}
