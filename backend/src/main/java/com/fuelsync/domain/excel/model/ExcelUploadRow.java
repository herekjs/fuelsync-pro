package com.fuelsync.domain.excel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcelUploadRow {

    private Long rowId;
    private Long batchId;
    private Integer rowNo;
    private LocalDate saleDate;
    /** C열 입고 */
    private BigDecimal gasolineInLiter;
    /** D열 판매 */
    private BigDecimal gasolineSaleLiter;
    /** I열 판매차 */
    private BigDecimal gasolineDiffLiter;
    /** J열 입고 */
    private BigDecimal dieselInLiter;
    /** K열 판매 */
    private BigDecimal dieselSaleLiter;
    /** P열 판매차 */
    private BigDecimal dieselDiffLiter;
    /** G열 휘발유 판매가 */
    private BigDecimal gasolineSellingPrice;
    /** H열 휘발유 재고가 */
    private BigDecimal gasolineBuyPrice;
    /** N열 경유 판매가 */
    private BigDecimal dieselSellingPrice;
    /** O열 경유 재고가 */
    private BigDecimal dieselBuyPrice;
    /** W열 휘발유 실측(L) */
    private BigDecimal adjGasolinePhysicalLiter;
    /** X열 휘발유 재고조정(L) */
    private BigDecimal adjGasolineDiffLiter;
    /** Y열 경유 실측(L) */
    private BigDecimal adjDieselPhysicalLiter;
    /** Z열 경유 재고조정(L) */
    private BigDecimal adjDieselDiffLiter;
    /** 계산 마감잔여(L): W·Y 있으면 해당값, 없으면 행 시작 잔량 + 입고 − 판매 */
    private BigDecimal gasolineClosingLiter;
    private BigDecimal dieselClosingLiter;
    /** 행 시작 잔량 − 마감잔여 (전일 마감 대비 조정량과 동일 의미) */
    private BigDecimal gasolineDayAdjLiter;
    private BigDecimal dieselDayAdjLiter;
    /** 이번 업로드에서 tb_stock_in / tb_transaction INSERT 여부 */
    private Boolean gasStockInInserted;
    private Boolean gasSaleInserted;
    private Boolean dieselStockInInserted;
    private Boolean dieselSaleInserted;
}
