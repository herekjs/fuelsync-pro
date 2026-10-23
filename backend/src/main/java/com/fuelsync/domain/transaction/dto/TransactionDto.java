package com.fuelsync.domain.transaction.dto;

import com.fuelsync.domain.transaction.model.Transaction;
import jakarta.validation.constraints.*;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionDto {

    public record CreateRequest(
            @NotNull(message = "탱크 ID는 필수입니다.")
            Long tankId,

            @NotNull(message = "판매량(L)은 필수입니다.")
            @DecimalMin(value = "0.001", message = "판매량은 0보다 커야 합니다.")
            BigDecimal liter,

            @NotNull(message = "리터당 판매가는 필수입니다.")
            @DecimalMin(value = "0.01")
            BigDecimal unitPrice,

            @NotNull(message = "결제 유형은 필수입니다.")
            Transaction.PaymentType paymentType,

            String approvalNo,
            LocalDateTime txDatetime
    ) {}

    @Builder
    public record Response(
            Long txId,
            Long tankId,
            String tankName,
            String fuelType,
            BigDecimal liter,
            BigDecimal unitPrice,
            BigDecimal totalAmount,
            String paymentType,
            String inputType,
            String approvalNo,
            LocalDateTime txDatetime
    ) {
        public static Response from(Transaction tx) {
            Transaction.InputType it = tx.getInputType() != null ? tx.getInputType() : Transaction.InputType.WEB;
            return Response.builder()
                    .txId(tx.getTxId())
                    .tankId(tx.getTankId())
                    .tankName(tx.getTankName())
                    .fuelType(tx.getFuelType())
                    .liter(tx.getLiter())
                    .unitPrice(tx.getUnitPrice())
                    .totalAmount(tx.getTotalAmount())
                    .paymentType(tx.getPaymentType() != null ? tx.getPaymentType().name() : null)
                    .inputType(it.name())
                    .approvalNo(tx.getApprovalNo())
                    .txDatetime(tx.getTxDatetime())
                    .build();
        }
    }
}
