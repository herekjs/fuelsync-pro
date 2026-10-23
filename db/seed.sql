-- ============================================================
-- FuelSync Pro — 기준 시드 (데모용). schema.sql 실행 후 실행합니다.
--
--   • 탱크 tank_id 순서: 1 휘발유, 2 경유 (엑셀 업로드는 유종별 최소 tank_id 사용)
--   • 주유소 KATECH 좌표는 데모용 — 실운영 시 오피넷 지도 등으로 본인 좌표로 교체
--   • 로그인: username jsan / password 1234 (password_hash 는 BCrypt)
--     데모 전용 계정입니다. 운영 DB에는 이 시드를 그대로 쓰지 말고 비밀번호를 바꾸세요.
-- ============================================================

SET NAMES utf8mb4;

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
