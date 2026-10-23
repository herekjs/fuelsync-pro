-- FuelSync Pro — 누적 데이터만 삭제 (테이블·스키마 유지)
-- 삭제 대상: 엑셀 업로드 이력, 일별 마감 잔량, 판매, 입고, 재고 조정, 가격 이력
-- (앱의 "운영 데이터 삭제" 기능과 같은 범위)
-- 유지: 사용자, 탱크, 주유소 정보, 판매가. 탱크 current_volume 은 그대로 남습니다.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM tb_excel_upload_row;
DELETE FROM tb_excel_upload_batch;
DELETE FROM tb_tank_daily_level;
DELETE FROM tb_transaction;
DELETE FROM tb_stock_in;
DELETE FROM tb_stock_adjustment;
DELETE FROM tb_price_history;

SET FOREIGN_KEY_CHECKS = 1;

ALTER TABLE tb_excel_upload_row AUTO_INCREMENT = 1;
ALTER TABLE tb_excel_upload_batch AUTO_INCREMENT = 1;
ALTER TABLE tb_tank_daily_level AUTO_INCREMENT = 1;
ALTER TABLE tb_transaction AUTO_INCREMENT = 1;
ALTER TABLE tb_stock_in AUTO_INCREMENT = 1;
ALTER TABLE tb_stock_adjustment AUTO_INCREMENT = 1;
ALTER TABLE tb_price_history AUTO_INCREMENT = 1;
