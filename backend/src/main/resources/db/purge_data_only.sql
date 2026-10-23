-- FuelSync Pro — 데이터만 삭제 (테이블·스키마 유지)
-- 루트 `db/purge_data_only.sql` 과 동일

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM tb_excel_upload_row;
DELETE FROM tb_excel_upload_batch;
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
ALTER TABLE tb_tank_daily_level AUTO_INCREMENT = 1;
