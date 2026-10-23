package com.fuelsync.domain.transaction.service;

import com.fuelsync.domain.tank.model.Tank;
import com.fuelsync.domain.tank.mapper.TankMapper;
import com.fuelsync.domain.tank.service.TankService;
import com.fuelsync.domain.transaction.dto.TransactionDto;
import com.fuelsync.domain.transaction.mapper.TransactionMapper;
import com.fuelsync.domain.transaction.model.Transaction;
import com.fuelsync.websocket.StockEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionMapper transactionMapper;
    private final TankMapper tankMapper;
    private final TankService tankService;
    private final StockEventPublisher stockEventPublisher;

    public List<TransactionDto.Response> findByPeriod(LocalDateTime from, LocalDateTime to) {
        return transactionMapper.findByPeriod(from, to)
                .stream()
                .map(TransactionDto.Response::from)
                .toList();
    }

    public List<TransactionDto.Response> findRecentOneHour() {
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        return transactionMapper.findRecent(since)
                .stream()
                .map(TransactionDto.Response::from)
                .toList();
    }

    @Transactional
    public TransactionDto.Response create(TransactionDto.CreateRequest request) {
        Tank tank = tankService.getTankForUpdateOrThrow(request.tankId());

        BigDecimal totalAmount = request.liter()
                .multiply(request.unitPrice())
                .setScale(2, RoundingMode.HALF_UP);

        // 탱크 잔량 차감 + 상태 재산정 (메모리)
        tank.deductVolume(request.liter());
        // DB에 명시적 반영
        tankMapper.updateVolume(tank);

        Transaction tx = Transaction.builder()
                .tankId(tank.getTankId())
                .tankName(tank.getTankName())
                .fuelType(tank.getFuelType().name())
                .liter(request.liter())
                .unitPrice(request.unitPrice())
                .totalAmount(totalAmount)
                .paymentType(request.paymentType())
                .inputType(Transaction.InputType.WEB)
                .approvalNo(request.approvalNo())
                .txDatetime(request.txDatetime() != null ? request.txDatetime() : LocalDateTime.now())
                .build();

        transactionMapper.insert(tx);
        log.info("[Transaction] create txId={}, tankId={}, liter={}L", tx.getTxId(), tank.getTankId(), request.liter());

        stockEventPublisher.publishTankChange(tank);

        return TransactionDto.Response.from(tx);
    }
}
