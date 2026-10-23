package com.fuelsync.domain.stockin.service;

import com.fuelsync.domain.stockin.dto.StockInDto;
import com.fuelsync.domain.stockin.mapper.StockInMapper;
import com.fuelsync.domain.stockin.model.StockIn;
import com.fuelsync.domain.tank.mapper.TankMapper;
import com.fuelsync.domain.tank.model.Tank;
import com.fuelsync.domain.tank.service.TankService;
import com.fuelsync.websocket.StockEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockInService {

    private final StockInMapper stockInMapper;
    private final TankMapper tankMapper;
    private final TankService tankService;
    private final StockEventPublisher stockEventPublisher;

    public List<StockInDto.Response> findByTank(Long tankId) {
        return stockInMapper.findByTankId(tankId)
                .stream()
                .map(StockInDto.Response::from)
                .toList();
    }

    public List<StockInDto.Response> findByPeriod(LocalDate from, LocalDate to) {
        return stockInMapper.findByPeriod(from, to)
                .stream()
                .map(StockInDto.Response::from)
                .toList();
    }

    @Transactional
    public StockInDto.Response create(StockInDto.CreateRequest request) {
        Tank tank = tankService.getTankOrThrow(request.tankId());

        // 탱크 잔량 증가 + 용량 초과 검증 (메모리)
        tank.addVolume(request.inVolume());
        // DB에 명시적 반영
        tankMapper.updateVolume(tank);

        StockIn stockIn = StockIn.builder()
                .tankId(tank.getTankId())
                .tankName(tank.getTankName())
                .fuelType(tank.getFuelType().name())
                .inVolume(request.inVolume())
                .buyPrice(request.buyPrice())
                .vendorName(request.vendorName())
                .inDate(request.inDate())
                .memo(request.memo())
                .inputType("MANUAL")
                .build();

        stockInMapper.insert(stockIn);
        log.info("[StockIn] create inId={}, tankId={}, volume={}L, price={}", stockIn.getInId(), tank.getTankId(), request.inVolume(), request.buyPrice());

        stockEventPublisher.publishStockUpdate(new StockEventPublisher.StockUpdateMessage(
                tank.getTankId(),
                tank.getTankName(),
                tank.getFuelType().name(),
                tank.getCurrentVolume().doubleValue(),
                tank.getMaxCapacity().doubleValue(),
                tank.getFillRatio().doubleValue(),
                tank.getStatus().name()
        ));

        return StockInDto.Response.from(stockIn);
    }
}
