package com.fuelsync.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 실시간 재고 변동 이벤트를 WebSocket 구독자에게 브로드캐스트한다.
 * 판매(Transaction) 또는 입고(StockIn) 처리 후 Service 레이어에서 호출한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

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
