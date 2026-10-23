package com.fuelsync.domain.maintenance.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationalDataPurgeMapper {

    void deleteAllExcelUploadRows();

    void deleteAllExcelUploadBatches();

    void deleteAllTankDailyLevels();

    void deleteAllTransactions();

    void deleteAllStockIns();

    void deleteAllStockAdjustments();

    void deleteAllPriceHistory();

    void resetExcelUploadRowAutoIncrement();

    void resetExcelUploadBatchAutoIncrement();

    void resetTransactionAutoIncrement();

    void resetStockInAutoIncrement();

    void resetStockAdjustmentAutoIncrement();

    void resetPriceHistoryAutoIncrement();
}
