package com.fuelsync.domain.excel.dto;

public record ExcelUploadResult(
        long batchId,
        int transactionInserted,
        int stockInInserted,
        int overwrittenTransactions,
        int skippedDates,
        int adjustmentInserted,
        int adjustmentSkipped,
        int adjustmentExcelDeletes) {

    /** 판매 + 입고 저장 건수 합계 (대시보드 토스트 기본값과 동일) */
    public int inserted() {
        return transactionInserted + stockInInserted;
    }
}
