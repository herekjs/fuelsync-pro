package com.fuelsync.common.exception;

import lombok.Getter;

@Getter
public class FuelSyncException extends RuntimeException {

    private final String errorCode;

    public FuelSyncException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    // 도메인별 에러 코드 상수
    public static final class Tank {
        public static final String NOT_FOUND       = "TANK_001";
        public static final String INSUFFICIENT    = "TANK_002";  // 잔량 부족
        public static final String OVERFLOW        = "TANK_003";  // 용량 초과
        public static final String INVALID_INVENTORY = "TANK_004";  // 최대 용량·재고 값 오류
    }

    public static final class Transaction {
        public static final String NOT_FOUND       = "TRAN_001";
        public static final String INVALID_AMOUNT  = "TRAN_002";
        public static final String EXCEL_INVALID     = "TRAN_003";
    }

    public static final class StockIn {
        public static final String NOT_FOUND       = "STKI_001";
        public static final String OVERFLOW        = "STKI_002";  // 탱크 용량 초과 입고
    }

    public static final class Adjustment {
        public static final String NOT_FOUND       = "ADJT_001";
        public static final String DUPLICATE       = "ADJT_002";  // 당일 보정 중복
        public static final String EXCEL_INVALID   = "ADJT_003";
    }

    public static final class Price {
        public static final String NOT_FOUND       = "PRIC_001";
        public static final String DUPLICATE       = "PRIC_002";
    }

    public static final class Auth {
        public static final String INVALID_CREDENTIALS = "AUTH_001";
        public static final String REQUIRED              = "AUTH_002";
        public static final String FORBIDDEN             = "AUTH_003";
    }
}
