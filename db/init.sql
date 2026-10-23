-- ============================================================
-- FuelSync Pro — MariaDB 통합 초기화 (구 db/V*.sql 일원화)
--
-- 사용법
--   • 전체 초기화: 이 파일 전체 실행 (DROP 후 CREATE + 탱크 시드)
--   • 데이터만 비우기: `purge_data_only.sql` 실행, 또는 이 파일 하단 [선택 B] 주석 해제
-- ============================================================

SET NAMES utf8mb4;

-- =============================================================================
-- [1] 전 객체 삭제 — 과거 스키마·데이터 일괄 제거
-- =============================================================================
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS tb_excel_upload_row;
DROP TABLE IF EXISTS tb_excel_upload_batch;
DROP TABLE IF EXISTS tb_transaction;
DROP TABLE IF EXISTS tb_stock_in;
DROP TABLE IF EXISTS tb_stock_adjustment;
DROP TABLE IF EXISTS tb_price_history;
DROP TABLE IF EXISTS tb_selling_price;
DROP TABLE IF EXISTS tb_station_info;
DROP TABLE IF EXISTS tb_user;
DROP TABLE IF EXISTS tb_tank;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- [2] 테이블 생성 (최종 스키마)
-- =============================================================================
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------
-- 사용자
-- ------------------------------------------------------------
CREATE TABLE tb_user (
    user_id       BIGINT          NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)     NOT NULL,
    password_hash VARCHAR(255)    NOT NULL,
    role          VARCHAR(10)     NOT NULL DEFAULT 'STAFF',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id),
    UNIQUE KEY uq_user_username (username),
    CONSTRAINT chk_user_role CHECK (role IN ('OWNER', 'STAFF'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 탱크
-- ------------------------------------------------------------
CREATE TABLE tb_tank (
    tank_id           BIGINT          NOT NULL AUTO_INCREMENT,
    tank_name         VARCHAR(30)     NOT NULL,
    fuel_type         VARCHAR(20)     NOT NULL,
    max_capacity      DECIMAL(10, 2)  NOT NULL,
    current_volume    DECIMAL(10, 2)  NOT NULL DEFAULT 0,
    temperature       DECIMAL(5, 2)   NULL,
    water_level       DECIMAL(5, 2)   NULL DEFAULT 0,
    threshold_warning INT             NOT NULL DEFAULT 20,
    threshold_danger  INT             NOT NULL DEFAULT 10,
    status            VARCHAR(10)     NOT NULL DEFAULT 'NORMAL',
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (tank_id),
    CONSTRAINT chk_tank_fuel_type CHECK (fuel_type IN ('GASOLINE', 'DIESEL', 'LPG', 'PREMIUM')),
    CONSTRAINT chk_tank_status    CHECK (status IN ('NORMAL', 'WARNING', 'DANGER')),
    CONSTRAINT chk_tank_capacity  CHECK (max_capacity > 0),
    CONSTRAINT chk_tank_volume    CHECK (current_volume >= 0 AND current_volume <= max_capacity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 판매
-- ------------------------------------------------------------
CREATE TABLE tb_transaction (
    tx_id         BIGINT          NOT NULL AUTO_INCREMENT,
    tank_id       BIGINT          NOT NULL,
    liter         DECIMAL(10, 3)  NOT NULL,
    unit_price    DECIMAL(10, 2)  NOT NULL,
    total_amount  DECIMAL(15, 2)  NOT NULL,
    payment_type  VARCHAR(10)     NULL COMMENT 'CARD | CASH | APP, NULL=미입력',
    input_type    VARCHAR(10)     NOT NULL DEFAULT 'WEB' COMMENT 'WEB | EXCEL',
    approval_no   VARCHAR(30)     NULL,
    tx_datetime   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (tx_id),
    CONSTRAINT fk_tx_tank         FOREIGN KEY (tank_id) REFERENCES tb_tank (tank_id),
    CONSTRAINT chk_tx_payment     CHECK (payment_type IS NULL OR payment_type IN ('CARD', 'CASH', 'APP')),
    CONSTRAINT chk_tx_input_type  CHECK (input_type IN ('WEB', 'EXCEL')),
    CONSTRAINT chk_tx_liter       CHECK (liter > 0),
    CONSTRAINT chk_tx_unit_price  CHECK (unit_price > 0),
    CONSTRAINT chk_tx_total       CHECK (total_amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 입고
-- ------------------------------------------------------------
CREATE TABLE tb_stock_in (
    in_id       BIGINT          NOT NULL AUTO_INCREMENT,
    tank_id     BIGINT          NOT NULL,
    in_volume   DECIMAL(10, 2)  NOT NULL,
    buy_price   DECIMAL(10, 2)  NOT NULL,
    vendor_name VARCHAR(100)    NULL,
    in_date     DATE            NOT NULL,
    memo        VARCHAR(255)    NULL,
    input_type  VARCHAR(10)     NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL | EXCEL',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (in_id),
    CONSTRAINT fk_stockin_tank    FOREIGN KEY (tank_id) REFERENCES tb_tank (tank_id),
    CONSTRAINT chk_stock_in_input_type CHECK (input_type IN ('MANUAL', 'EXCEL')),
    CONSTRAINT chk_stockin_vol    CHECK (in_volume > 0),
    CONSTRAINT chk_stockin_price  CHECK (buy_price > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 재고 조정
-- ------------------------------------------------------------
CREATE TABLE tb_stock_adjustment (
    adj_id          BIGINT          NOT NULL AUTO_INCREMENT,
    tank_id         BIGINT          NOT NULL,
    book_stock      DECIMAL(10, 2)  NOT NULL,
    physical_stock  DECIMAL(10, 2)  NOT NULL,
    diff_volume     DECIMAL(10, 2)  NOT NULL,
    diff_amount     DECIMAL(15, 2)  NOT NULL,
    adj_date        DATE            NOT NULL,
    memo            VARCHAR(255)    NULL,
    input_type      VARCHAR(10)     NOT NULL DEFAULT 'WEB' COMMENT 'WEB | EXCEL',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (adj_id),
    CONSTRAINT fk_adj_tank  FOREIGN KEY (tank_id) REFERENCES tb_tank (tank_id),
    CONSTRAINT chk_adj_input_type CHECK (input_type IN ('WEB', 'EXCEL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX uix_adj_tank_date ON tb_stock_adjustment (tank_id, adj_date);

-- ------------------------------------------------------------
-- 가격 이력 (오피넷 / 자체 판매가)
-- ------------------------------------------------------------
CREATE TABLE tb_price_history (
    history_id           BIGINT          NOT NULL AUTO_INCREMENT,
    fuel_type            VARCHAR(20)     NOT NULL,
    our_price            DECIMAL(10, 2)  NOT NULL,
    competitor_avg_price DECIMAL(10, 2)  NULL,
    opinet_date          DATE            NOT NULL,
    created_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (history_id),
    CONSTRAINT chk_price_fuel CHECK (fuel_type IN ('GASOLINE', 'DIESEL', 'LPG', 'PREMIUM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX uix_price_fuel_date ON tb_price_history (fuel_type, opinet_date);

-- ------------------------------------------------------------
-- 주유소 기본 정보 · 유종별 판매가 이력
-- ------------------------------------------------------------
CREATE TABLE tb_station_info (
    station_id   BIGINT       NOT NULL AUTO_INCREMENT,
    station_name VARCHAR(100) NOT NULL,
    address      VARCHAR(200) NULL,
    katech_x     BIGINT       NULL,
    katech_y     BIGINT       NULL,
    phone        VARCHAR(20)  NULL,
    brand        VARCHAR(50)  NULL,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (station_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tb_selling_price (
    price_id       BIGINT         NOT NULL AUTO_INCREMENT,
    fuel_type      VARCHAR(20)    NOT NULL,
    selling_price  DECIMAL(10, 2) NOT NULL,
    effective_from DATE           NOT NULL,
    created_at     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (price_id),
    UNIQUE KEY uq_fuel_type_date (fuel_type, effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 엑셀 업로드 이력
-- ------------------------------------------------------------
CREATE TABLE tb_excel_upload_batch (
    batch_id        BIGINT          NOT NULL AUTO_INCREMENT,
    file_name       VARCHAR(255)    NOT NULL,
    total_rows      INT             NOT NULL DEFAULT 0 COMMENT '파싱된 유효 엑셀 행 수',
    inserted_count      INT             NOT NULL DEFAULT 0 COMMENT '판매(tb_transaction)+입고(tb_stock_in) 저장 건수 합계',
    applied_date_count  INT             NOT NULL DEFAULT 0 COMMENT '판매·입고가 INSERT된 서로 다른 일자 수',
    uploaded_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tb_excel_upload_row (
    row_id                       BIGINT          NOT NULL AUTO_INCREMENT,
    batch_id                     BIGINT          NOT NULL,
    row_no                       INT             NOT NULL COMMENT '엑셀 시트 행 번호 (0-based)',
    sale_date                    DATE            NOT NULL COMMENT 'A열',
    gasoline_in_liter            DECIMAL(10, 3)  NULL COMMENT 'C열 입고',
    gasoline_sale_liter          DECIMAL(10, 3)  NULL COMMENT 'D열 판매',
    gasoline_diff_liter          DECIMAL(10, 3)  NULL COMMENT 'I열 판매차',
    diesel_in_liter              DECIMAL(10, 3)  NULL COMMENT 'J열 입고',
    diesel_sale_liter            DECIMAL(10, 3)  NULL COMMENT 'K열 판매',
    diesel_diff_liter            DECIMAL(10, 3)  NULL COMMENT 'P열 판매차',
    gasoline_selling_price       DECIMAL(10, 2)  NULL COMMENT 'G열 휘발유 판매가',
    gasoline_buy_price           DECIMAL(10, 2)  NULL COMMENT 'H열 휘발유 재고가',
    diesel_selling_price         DECIMAL(10, 2)  NULL COMMENT 'N열 경유 판매가',
    diesel_buy_price             DECIMAL(10, 2)  NULL COMMENT 'O열 경유 재고가',
    adj_gasoline_physical_liter  DECIMAL(12, 3)  NULL COMMENT 'W열 휘발유 실측(L)',
    adj_gasoline_diff_liter      DECIMAL(12, 3)  NULL COMMENT 'X열 휘발유 재고조정(L)',
    adj_diesel_physical_liter    DECIMAL(12, 3)  NULL COMMENT 'Y열 경유 실측(L)',
    adj_diesel_diff_liter        DECIMAL(12, 3)  NULL COMMENT 'Z열 경유 재고조정(L)',
    gasoline_closing_liter       DECIMAL(12, 3)  NULL COMMENT '계산 마감잔여(L)',
    diesel_closing_liter         DECIMAL(12, 3)  NULL COMMENT '계산 마감잔여(L)',
    gasoline_day_adj_liter       DECIMAL(12, 3)  NULL COMMENT '조정량(L): 행시작-마감',
    diesel_day_adj_liter         DECIMAL(12, 3)  NULL COMMENT '조정량(L): 행시작-마감',
    gas_stock_in_inserted        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'C열 입고 INSERT 여부',
    gas_sale_inserted            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'D열 판매 INSERT 여부',
    diesel_stock_in_inserted     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'J열 입고 INSERT 여부',
    diesel_sale_inserted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'K열 판매 INSERT 여부',

    PRIMARY KEY (row_id),
    KEY idx_excel_row_batch (batch_id),
    CONSTRAINT fk_excel_row_batch FOREIGN KEY (batch_id) REFERENCES tb_excel_upload_batch (batch_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tb_tank_daily_level (
    level_id           BIGINT          NOT NULL AUTO_INCREMENT,
    tank_id            BIGINT          NOT NULL,
    level_date         DATE            NOT NULL,
    end_volume_liters  DECIMAL(12, 3)  NOT NULL,
    source             VARCHAR(20)     NOT NULL DEFAULT 'EXCEL',
    updated_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (level_id),
    UNIQUE KEY uq_tank_daily_level_tank_date (tank_id, level_date),
    KEY idx_tank_daily_level_date (level_date),
    CONSTRAINT fk_tank_daily_level_tank FOREIGN KEY (tank_id) REFERENCES tb_tank (tank_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- [3] 기준 시드 — 탱크 · 주유소 · 판매가 · 데모 거래/입고·오피넷식 가격이력
--    • 탱크 tank_id 순서: 1~2 휘발유, 3~4 경유, 5 LPG (엑셀 업로드는 유종별 최소 tank_id 사용)
--    • 주유소 KATECH 좌표는 데모용 — 실운영 시 오피넷 지도 등으로 본인 좌표로 교체
--    • 아래 입고·판매(tb_stock_in / tb_transaction)는 차트 데모용이며 tb_tank.current_volume 과
--      장부상 재고는 일치하지 않을 수 있음
--    • 로그인: username jsan / password 1234 (password_hash 는 BCrypt)
-- =============================================================================
INSERT INTO tb_user (username, password_hash, role)
VALUES (
    'jsan',
    '$2a$10$hZt66xgrrf9btUFSnCKN8uQNpKR5xbGcUMSBuA/eMO8IZZlJBccFG',
    'OWNER'
);

INSERT INTO tb_tank (tank_name, fuel_type, max_capacity, current_volume, threshold_warning, threshold_danger, status)
VALUES
    ('Tank G', 'GASOLINE', 50000.00, 34831.00, 20, 10, 'NORMAL'),
    ('Tank D', 'DIESEL',   50000.00, 32989.00, 20, 10, 'NORMAL');

INSERT INTO tb_station_info (station_name, address, katech_x, katech_y, phone, brand)
VALUES (
    'FuelSync 데모 주유소',
    '광주광역시 서구 시청로 데모',
    291165,
    189065,
    '062-000-0000',
    '데모브랜드'
);

INSERT INTO tb_selling_price (fuel_type, selling_price, effective_from)
VALUES
    ('GASOLINE', 1999.00, '2026-01-01'),
    ('DIESEL',   1999.00, '2026-01-01');



-- ############################################################################
-- [선택 B] 스키마 유지 — 누적 데이터만 삭제 (과거 기록 초기화)

-- 이미 [1][2]로 최신 스키마가 있다고 가정하고, 행만 비웁니다.
-- 실행 후 탱크·주유소 등은 비어 있거나 그대로이므로, 필요 시 [3] 시드나 앱에서 다시 등록하세요.
-- ############################################################################

SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM tb_excel_upload_row;
DELETE FROM tb_excel_upload_batch;
DELETE FROM tb_tank_daily_level;
DELETE FROM tb_transaction;
DELETE FROM tb_stock_in;
DELETE FROM tb_stock_adjustment;
DELETE FROM tb_price_history;
DELETE FROM tb_selling_price;
DELETE FROM tb_station_info;
DELETE FROM tb_user;
DELETE FROM tb_tank;

SET FOREIGN_KEY_CHECKS = 1;

ALTER TABLE tb_excel_upload_row AUTO_INCREMENT = 1;
ALTER TABLE tb_excel_upload_batch AUTO_INCREMENT = 1;
ALTER TABLE tb_transaction AUTO_INCREMENT = 1;
ALTER TABLE tb_stock_in AUTO_INCREMENT = 1;
ALTER TABLE tb_stock_adjustment AUTO_INCREMENT = 1;
ALTER TABLE tb_price_history AUTO_INCREMENT = 1;
ALTER TABLE tb_selling_price AUTO_INCREMENT = 1;
ALTER TABLE tb_station_info AUTO_INCREMENT = 1;
ALTER TABLE tb_user AUTO_INCREMENT = 1;
ALTER TABLE tb_tank AUTO_INCREMENT = 1;

