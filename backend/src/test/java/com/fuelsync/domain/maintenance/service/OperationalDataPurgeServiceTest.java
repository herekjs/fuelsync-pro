package com.fuelsync.domain.maintenance.service;

import com.fuelsync.domain.maintenance.mapper.OperationalDataPurgeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationalDataPurgeServiceTest {

    private OperationalDataPurgeMapper mapper;
    private PlatformTransactionManager txManager;
    private OperationalDataPurgeService service;

    @BeforeEach
    void setUp() {
        mapper = mock(OperationalDataPurgeMapper.class);
        txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        service = new OperationalDataPurgeService(mapper, new TransactionTemplate(txManager));
    }

    @Test
    void purge_deletesAllRowsInOneTransactionThenResetsAutoIncrement() {
        service.purgeUploadAndOperationalTables("jsan");

        InOrder order = inOrder(mapper, txManager);
        order.verify(txManager).getTransaction(any());
        order.verify(mapper).deleteAllExcelUploadRows();
        order.verify(mapper).deleteAllExcelUploadBatches();
        order.verify(mapper).deleteAllTankDailyLevels();
        order.verify(mapper).deleteAllTransactions();
        order.verify(mapper).deleteAllStockIns();
        order.verify(mapper).deleteAllStockAdjustments();
        order.verify(mapper).deleteAllPriceHistory();
        order.verify(txManager).commit(any());
        // DDL(ALTER TABLE)은 삭제 트랜잭션이 커밋된 뒤에만 실행한다.
        order.verify(mapper).resetExcelUploadRowAutoIncrement();
        order.verify(mapper).resetExcelUploadBatchAutoIncrement();
        order.verify(mapper).resetTankDailyLevelAutoIncrement();
        order.verify(mapper).resetTransactionAutoIncrement();
        order.verify(mapper).resetStockInAutoIncrement();
        order.verify(mapper).resetStockAdjustmentAutoIncrement();
        order.verify(mapper).resetPriceHistoryAutoIncrement();
    }

    @Test
    void purge_whenDeleteFails_rollsBackAndSkipsAutoIncrementReset() {
        doThrow(new IllegalStateException("boom")).when(mapper).deleteAllTransactions();

        assertThrows(IllegalStateException.class, () -> service.purgeUploadAndOperationalTables("jsan"));

        verify(txManager).rollback(any());
        verify(txManager, never()).commit(any());
        verify(mapper, never()).resetExcelUploadRowAutoIncrement();
        verify(mapper, never()).resetTransactionAutoIncrement();
    }
}
