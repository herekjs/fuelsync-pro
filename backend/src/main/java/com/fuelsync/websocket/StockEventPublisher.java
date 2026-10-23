package com.fuelsync.websocket;

import com.fuelsync.domain.tank.model.Tank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 실시간 재고 변동 이벤트를 WebSocket 구독자에게 브로드캐스트한다.
 * 탱크 잔량을 바꾸는 Service 레이어(판매·입고·재고 조정·용량 수정·엑셀 업로드)에서
 * {@link #publishTankChange(Tank)} 로 호출한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 탱크 잔량이 바뀐 뒤 호출한다. 재고 업데이트를 발행하고, 상태가 NORMAL이 아니면 알림도 발행한다.
     * 활성 트랜잭션이 있으면 커밋 이후에 발행하므로 롤백된 변경은 전파되지 않는다.
     */
    public void publishTankChange(Tank tank) {
        StockUpdateMessage update = new StockUpdateMessage(
                tank.getTankId(),
                tank.getTankName(),
                tank.getFuelType().name(),
                tank.getCurrentVolume().doubleValue(),
                tank.getMaxCapacity().doubleValue(),
                tank.getFillRatio().doubleValue(),
                tank.getStatus().name());
        AlertMessage alert = tank.getStatus() != Tank.TankStatus.NORMAL
                ? new AlertMessage(
                        tank.getTankId(),
                        tank.getTankName(),
                        tank.getFuelType().name(),
                        tank.getStatus().name(),
                        tank.getCurrentVolume().doubleValue(),
                        tank.getFillRatio().doubleValue())
                : null;

        Runnable send = () -> {
            try {
                publishStockUpdate(update);
                if (alert != null) {
                    publishAlert(alert);
                }
            } catch (RuntimeException e) {
                // 이미 커밋된 DB 변경에는 영향이 없도록 발행 실패는 로그만 남긴다.
                log.warn("[WS] publish failed tankId={}: {}", tank.getTankId(), e.getMessage());
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    /**
     * 탱크 재고 변동 이벤트 발행
     * 구독 주소: /topic/stock/{tankId}
     */
    public void publishStockUpdate(StockUpdateMessage message) {
        log.debug("[WS] stock update tankId={}, volume={}", message.tankId(), message.currentVolume());
        messagingTemplate.convertAndSend("/topic/stock/" + message.tankId(), message);
    }

    /**
     * 탱크 상태(WARNING/DANGER) 변경 알림
     * 구독 주소: /topic/alert
     */
    public void publishAlert(AlertMessage message) {
        log.info("[WS] alert tankId={}, status={}", message.tankId(), message.status());
        messagingTemplate.convertAndSend("/topic/alert", message);
    }

    public record StockUpdateMessage(
            Long tankId,
            String tankName,
            String fuelType,
            double currentVolume,
            double maxCapacity,
            double fillRatio,
            String status
    ) {}

    public record AlertMessage(
            Long tankId,
            String tankName,
            String fuelType,
            String status,
            double currentVolume,
            double fillRatio
    ) {}
}
