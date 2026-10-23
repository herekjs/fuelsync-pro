package com.fuelsync.domain.maintenance.service;

import com.fuelsync.domain.maintenance.mapper.OperationalDataPurgeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 누적 이력(엑셀 업로드·일별 마감 잔량·판매·입고·재고 조정·가격 이력)을 삭제한다.
 * 사용자·탱크·주유소·판매가는 유지하며, 탱크의 현재 잔량도 그대로 둔다(삭제 시점의 잔량이 새 기준이 된다).
 * 삭제 범위는 db/purge_data_only.sql 과 같아야 한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OperationalDataPurgeService {

    private final OperationalDataPurgeMapper operationalDataPurgeMapper;
    private final TransactionTemplate transactionTemplate;

    public void purgeUploadAndOperationalTables(String username) {
        // 행 삭제는 한 트랜잭션으로 묶어 중간에 실패하면 모두 되돌린다.
        transactionTemplate.executeWithoutResult(status -> deleteAllRows());

        // ALTER TABLE 은 MariaDB 에서 암묵 커밋을 일으키는 DDL 이라 삭제 트랜잭션이 끝난 뒤에 실행한다.
        resetAutoIncrements();

        log.warn("[OperationalDataPurge] All upload/operational tables purged by user={}", username);
    }

    private void deleteAllRows() {
        operationalDataPurgeMapper.deleteAllExcelUploadRows();
        operationalDataPurgeMapper.deleteAllExcelUploadBatches();
        operationalDataPurgeMapper.deleteAllTankDailyLevels();
        operationalDataPurgeMapper.deleteAllTransactions();
        operationalDataPurgeMapper.deleteAllStockIns();
        operationalDataPurgeMapper.deleteAllStockAdjustments();
        operationalDataPurgeMapper.deleteAllPriceHistory();
    }

    private void resetAutoIncrements() {
        operationalDataPurgeMapper.resetExcelUploadRowAutoIncrement();
        operationalDataPurgeMapper.resetExcelUploadBatchAutoIncrement();
        operationalDataPurgeMapper.resetTankDailyLevelAutoIncrement();
        operationalDataPurgeMapper.resetTransactionAutoIncrement();
        operationalDataPurgeMapper.resetStockInAutoIncrement();
        operationalDataPurgeMapper.resetStockAdjustmentAutoIncrement();
        operationalDataPurgeMapper.resetPriceHistoryAutoIncrement();
    }
}
