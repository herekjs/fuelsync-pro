-- FuelSync Pro — 데이터만 삭제 (테이블·스키마 유지)
-- FK 순서: 자식 → 부모. 실행 후 기본 탱크 등은 init.sql [3] 또는 앱에서 다시 넣으세요.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM tb_excel_upload_row;
DELETE FROM tb_excel_upload_batch;
DELETE FROM tb_transaction;
DELETE FROM tb_stock_in;
DELETE FROM tb_stock_adjustment;
DELETE FROM tb_price_history;

SET FOREIGN_KEY_CHECKS = 1;

ALTER TABLE tb_excel_upload_row AUTO_INCREMENT = 1;
ALTER TABLE tb_excel_upload_batch AUTO_INCREMENT = 1;
ALTER TABLE tb_transaction AUTO_INCREMENT = 1;
ALTER TABLE tb_stock_in AUTO_INCREMENT = 1;
ALTER TABLE tb_stock_adjustment AUTO_INCREMENT = 1;
ALTER TABLE tb_price_history AUTO_INCREMENT = 1;


