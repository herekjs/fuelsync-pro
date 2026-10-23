-- 기존 DB에 반영일수 컬럼 추가 (init.sql 새 설치에는 이미 포함됨)
ALTER TABLE tb_excel_upload_batch
    ADD COLUMN IF NOT EXISTS applied_date_count INT NOT NULL DEFAULT 0
        COMMENT '판매·입고가 INSERT된 서로 다른 일자 수' AFTER inserted_count;

-- 행 플래그가 있는 배치는 역산으로 반영일수 보정 (선택)
UPDATE tb_excel_upload_batch b
SET applied_date_count = (
    SELECT COUNT(DISTINCT r.sale_date)
    FROM tb_excel_upload_row r
    WHERE r.batch_id = b.batch_id
      AND (r.gas_stock_in_inserted = 1 OR r.gas_sale_inserted = 1
           OR r.diesel_stock_in_inserted = 1 OR r.diesel_sale_inserted = 1)
)
WHERE EXISTS (
    SELECT 1 FROM tb_excel_upload_row r
    WHERE r.batch_id = b.batch_id AND r.gas_stock_in_inserted IS NOT NULL
);
