-- 기존 DB에 판매·입고 INSERT 여부 플래그 추가 (schema.sql 새 설치에는 이미 포함됨)
ALTER TABLE tb_excel_upload_row
    ADD COLUMN IF NOT EXISTS gas_stock_in_inserted TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'C열 입고 INSERT 여부' AFTER diesel_day_adj_liter,
    ADD COLUMN IF NOT EXISTS gas_sale_inserted TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'D열 판매 INSERT 여부' AFTER gas_stock_in_inserted,
    ADD COLUMN IF NOT EXISTS diesel_stock_in_inserted TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'J열 입고 INSERT 여부' AFTER gas_sale_inserted,
    ADD COLUMN IF NOT EXISTS diesel_sale_inserted TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'K열 판매 INSERT 여부' AFTER diesel_stock_in_inserted;
