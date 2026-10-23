package com.fuelsync.domain.excel.dto;

import com.fuelsync.domain.excel.model.ExcelUploadBatch;
import com.fuelsync.domain.excel.model.ExcelUploadRow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ExcelUploadLogDto {

    private ExcelUploadLogDto() {
    }

    public record BatchResponse(
            Long batchId,
            String fileName,
            Integer totalRows,
            Integer insertedCount,
            Integer appliedDateCount,
            LocalDateTime uploadedAt
    ) {
        public static BatchResponse from(ExcelUploadBatch b) {
            return new BatchResponse(
                    b.getBatchId(),
                    b.getFileName(),
                    b.getTotalRows(),
                    b.getInsertedCount(),
                    b.getAppliedDateCount(),
                    b.getUploadedAt()
            );
        }
    }

    public record RowResponse(
            Long rowId,
            Integer rowNo,
            LocalDate saleDate,
            BigDecimal gasolineInLiter,
            BigDecimal gasolineSaleLiter,
            BigDecimal gasolineDiffLiter,
            BigDecimal gasolineSellingPrice,
            BigDecimal gasolineBuyPrice,
            BigDecimal dieselInLiter,
            BigDecimal dieselSaleLiter,
            BigDecimal dieselDiffLiter,
            BigDecimal dieselSellingPrice,
            BigDecimal dieselBuyPrice,
            BigDecimal adjGasolinePhysicalLiter,
            BigDecimal adjGasolineDiffLiter,
            BigDecimal adjDieselPhysicalLiter,
            BigDecimal adjDieselDiffLiter,
            BigDecimal gasolineClosingLiter,
            BigDecimal dieselClosingLiter,
            BigDecimal gasolineDayAdjLiter,
            BigDecimal dieselDayAdjLiter,
            Boolean gasStockInInserted,
            Boolean gasSaleInserted,
            Boolean dieselStockInInserted,
            Boolean dieselSaleInserted
    ) {
        public static RowResponse from(ExcelUploadRow r) {
            return new RowResponse(
                    r.getRowId(),
                    r.getRowNo(),
                    r.getSaleDate(),
                    r.getGasolineInLiter(),
                    r.getGasolineSaleLiter(),
                    r.getGasolineDiffLiter(),
                    r.getGasolineSellingPrice(),
                    r.getGasolineBuyPrice(),
                    r.getDieselInLiter(),
                    r.getDieselSaleLiter(),
                    r.getDieselDiffLiter(),
                    r.getDieselSellingPrice(),
                    r.getDieselBuyPrice(),
                    r.getAdjGasolinePhysicalLiter(),
                    r.getAdjGasolineDiffLiter(),
                    r.getAdjDieselPhysicalLiter(),
                    r.getAdjDieselDiffLiter(),
                    r.getGasolineClosingLiter(),
                    r.getDieselClosingLiter(),
                    r.getGasolineDayAdjLiter(),
                    r.getDieselDayAdjLiter(),
                    r.getGasStockInInserted(),
                    r.getGasSaleInserted(),
                    r.getDieselStockInInserted(),
                    r.getDieselSaleInserted()
            );
        }
    }
}
