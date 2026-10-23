-- 기존 DB에 일별 마감 잔량 테이블 추가 (schema.sql 새 설치에는 이미 포함됨)
CREATE TABLE IF NOT EXISTS tb_tank_daily_level (
    level_id           BIGINT         NOT NULL AUTO_INCREMENT,
    tank_id            BIGINT         NOT NULL,
    level_date         DATE           NOT NULL,
    end_volume_liters  DECIMAL(12, 3) NOT NULL,
    source             VARCHAR(20)    NOT NULL DEFAULT 'EXCEL',
    updated_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (level_id),
    UNIQUE KEY uq_tank_daily_level_tank_date (tank_id, level_date),
    KEY idx_tank_daily_level_date (level_date),
    CONSTRAINT fk_tank_daily_level_tank
        FOREIGN KEY (tank_id) REFERENCES tb_tank (tank_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
