package com.fuelsync.domain.transaction.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    private Long txId;
    private Long tankId;
    private String tankName;
    private String fuelType;
    private BigDecimal liter;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private PaymentType paymentType;
    private InputType inputType;
    private String approvalNo;
    private LocalDateTime txDatetime;
    private LocalDateTime createdAt;

    public enum PaymentType { CARD, CASH, APP }

    /** WEB: 화면 수동 등록, EXCEL: 엑셀 업로드 */
    public enum InputType { WEB, EXCEL }
}
