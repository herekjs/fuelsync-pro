package com.fuelsync.websocket;

import com.fuelsync.domain.tank.model.Tank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class StockEventPublisherTest {

    private SimpMessagingTemplate template;
    private StockEventPublisher publisher;

    @BeforeEach
    void setUp() {
        template = mock(SimpMessagingTemplate.class);
        publisher = new StockEventPublisher(template);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static Tank tank(String volume) {
        Tank tank = Tank.builder()
                .tankName("T")
                .fuelType(Tank.FuelType.GASOLINE)
                .maxCapacity(new BigDecimal("1000"))
                .currentVolume(new BigDecimal(volume))
                .build();
        tank.setTankId(7L);
        return tank;
    }

    @Test
    void withoutTransaction_normalTank_sendsOnlyStockUpdate() {
        publisher.publishTankChange(tank("500"));

        verify(template).convertAndSend(eq("/topic/stock/7"), any(StockEventPublisher.StockUpdateMessage.class));
        verify(template, never()).convertAndSend(eq("/topic/alert"), any(StockEventPublisher.AlertMessage.class));
    }

    @Test
    void withoutTransaction_dangerTank_alsoSendsAlert() {
        publisher.publishTankChange(tank("50"));

        verify(template).convertAndSend(eq("/topic/stock/7"), any(StockEventPublisher.StockUpdateMessage.class));
        verify(template).convertAndSend(eq("/topic/alert"), any(StockEventPublisher.AlertMessage.class));
    }

    @Test
    void withinTransaction_sendsOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishTankChange(tank("50"));
        verifyNoInteractions(template);

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        verify(template).convertAndSend(eq("/topic/stock/7"), any(StockEventPublisher.StockUpdateMessage.class));
        verify(template).convertAndSend(eq("/topic/alert"), any(StockEventPublisher.AlertMessage.class));
    }

    @Test
    void withinTransaction_rolledBack_sendsNothing() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishTankChange(tank("50"));
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verifyNoInteractions(template);
    }
}
