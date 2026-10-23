-- 기존 DB에 마감·일중 조정량 로그 컬럼 추가 (schema.sql 새 설치에는 이미 포함됨)
ALTER TABLE tb_excel_upload_row
    ADD COLUMN IF NOT EXISTS gasoline_closing_liter DECIMAL(12, 3) NULL COMMENT '계산 마감잔여(L)' AFTER adj_diesel_diff_liter,
    ADD COLUMN IF NOT EXISTS diesel_closing_liter DECIMAL(12, 3) NULL AFTER gasoline_closing_liter,
    ADD COLUMN IF NOT EXISTS gasoline_day_adj_liter DECIMAL(12, 3) NULL COMMENT '조정량(L): 행시작-마감' AFTER diesel_closing_liter,
    ADD COLUMN IF NOT EXISTS diesel_day_adj_liter DECIMAL(12, 3) NULL AFTER gasoline_day_adj_liter;
