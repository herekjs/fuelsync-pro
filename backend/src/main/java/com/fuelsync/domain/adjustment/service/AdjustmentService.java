package com.fuelsync.domain.adjustment.service;

import com.fuelsync.common.exception.FuelSyncException;
import com.fuelsync.domain.adjustment.dto.AdjustmentDto;
import com.fuelsync.domain.adjustment.mapper.StockAdjustmentMapper;
import com.fuelsync.domain.adjustment.model.StockAdjustment;
import com.fuelsync.domain.stockin.mapper.StockInMapper;
import com.fuelsync.domain.tank.mapper.TankMapper;
import com.fuelsync.domain.tank.model.Tank;
import com.fuelsync.domain.tank.service.TankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdjustmentService {

    private final StockAdjustmentMapper adjustmentMapper;
    private final StockInMapper stockInMapper;
    private final TankService tankService;
    private final TankMapper tankMapper;

    public List<AdjustmentDto.Response> findByPeriod(LocalDate from, LocalDate to) {
        return adjustmentMapper.findByPeriod(from, to)
                .stream()
                .map(AdjustmentDto.Response::from)
                .toList();
    }

    public List<AdjustmentDto.Response> findByTank(Long tankId) {
        return adjustmentMapper.findByTankId(tankId)
                .stream()
                .map(AdjustmentDto.Response::from)
                .toList();
    }

    @Transactional
    public AdjustmentDto.Response create(AdjustmentDto.CreateRequest request) {
        if (adjustmentMapper.existsByTankIdAndAdjDate(request.tankId(), request.adjDate())) {
            throw new FuelSyncException(
                    FuelSyncException.Adjustment.DUPLICATE,
                    "해당 날짜에 동일 탱크의 재고 보정이 이미 존재합니다.");
        }
        return insertAdjustment(request, StockAdjustment.InputType.WEB);
    }

    public boolean existsByTankAndDate(Long tankId, LocalDate adjDate) {
        return adjustmentMapper.existsByTankIdAndAdjDate(tankId, adjDate);
    }

    /**
     * 엑셀 업로드용. 해당 일자·탱크에 이미 보정이 있으면 삽입하지 않고 empty를 반환합니다.
     * 부모 트랜잭션(uploadExcel)에 반드시 참여합니다.
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Optional<AdjustmentDto.Response> createExcelIfAbsent(AdjustmentDto.CreateRequest request) {
        if (adjustmentMapper.existsByTankIdAndAdjDate(request.tankId(), request.adjDate())) {
            return Optional.empty();
        }
        return Optional.of(insertAdjustment(request, StockAdjustment.InputType.EXCEL));
    }

    /** 통합 엑셀 덮어쓰기 시 해당 일자·탱크의 엑셀 재고 조정만 삭제. 부모 트랜잭션에 반드시 참여합니다. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public int deleteExcelByTankAndDate(Long tankId, LocalDate adjDate) {
        return adjustmentMapper.deleteExcelByTankAndDate(tankId, adjDate);
    }

    private AdjustmentDto.Response insertAdjustment(
            AdjustmentDto.CreateRequest request,
            StockAdjustment.InputType inputType) {
        Tank tank = tankService.getTankOrThrow(request.tankId());

        BigDecimal diffVolume = request.physicalStock()
                .subtract(request.bookStock())
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal avgBuyPrice = stockInMapper.findAvgBuyPriceSince(
                request.tankId(), LocalDate.now().minusMonths(3));
        BigDecimal unitPrice = avgBuyPrice != null ? avgBuyPrice : BigDecimal.ZERO;
        BigDecimal diffAmount = diffVolume.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);

        StockAdjustment adj = StockAdjustment.builder()
                .tankId(tank.getTankId())
                .tankName(tank.getTankName())
                .fuelType(tank.getFuelType().name())
                .bookStock(request.bookStock())
                .physicalStock(request.physicalStock())
                .diffVolume(diffVolume)
                .diffAmount(diffAmount)
                .adjDate(request.adjDate())
                .memo(request.memo())
                .inputType(inputType)
                .build();

        adjustmentMapper.insert(adj);

        if (diffVolume.compareTo(BigDecimal.ZERO) > 0) {
            tank.addVolume(diffVolume);
        } else if (diffVolume.compareTo(BigDecimal.ZERO) < 0) {
            tank.deductVolume(diffVolume.abs());
        }
        if (diffVolume.compareTo(BigDecimal.ZERO) != 0) {
            tankMapper.updateVolume(tank);
        }

        log.info("[Adjustment] tankId={}, inputType={}, diffVolume={}L, diffAmount={}원",
                tank.getTankId(), inputType, diffVolume, diffAmount);
        return AdjustmentDto.Response.from(adj);
    }
}
