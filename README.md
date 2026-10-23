# FuelSync Pro — 지능형 통합 주유소 재고관리 시스템

## 기술 스택

| 영역 | 기술 |
|------|------|
| Backend | Spring Boot 3.2, Java 17, MyBatis, Spring WebSocket |
| Frontend | React 18, TypeScript, Vite, Tailwind CSS, ApexCharts |
| Database | MariaDB |
| 실시간 | STOMP over WebSocket (SockJS) |

## 프로젝트 구조

```
fuelsync-pro/
├── backend/         Spring Boot 백엔드
├── frontend/        React 프론트엔드
└── db/              SQL의 유일한 위치
    ├── schema.sql           최종 스키마 (새 설치용, 기존 tb_* 는 DROP 후 재생성)
    ├── seed.sql             데모 기준 시드 (계정 jsan · 탱크 2개 · 주유소 · 판매가)
    ├── purge_data_only.sql  테이블 유지, 누적 데이터만 삭제 + AI 리셋
    └── migrations/          기존 DB 업그레이드용 번호 순 변경 (001 → 004)
```

## `db/` 구성

MariaDB **저장 함수·프로시저(`CREATE FUNCTION` 등)는 없습니다.**

| 파일 | 역할 |
|------|------|
| `schema.sql` | `[1]` 전 객체 삭제 → `[2]` 10개 테이블 DDL (인덱스·CHECK·FK 포함) |
| `seed.sql` | 사용자(`jsan`/`1234`, 데모 전용)·탱크·주유소·판매가 INSERT |
| `purge_data_only.sql` | 업로드 이력·일별 마감 잔량·판매·입고·조정·가격 이력만 삭제 (앱의 "운영 데이터 삭제"와 같은 범위) |
| `migrations/NNN_*.sql` | 이미 운영 중인 DB에 컬럼·테이블을 추가하는 변경. 번호 순서대로, 아직 적용하지 않은 것만 실행 |

스키마를 바꿀 때는 `schema.sql` 과 `migrations/` 에 새 번호 파일을 **함께** 수정합니다.

## 로컬 개발 환경 설정

### 1. 데이터베이스 생성

```sql
CREATE DATABASE fuelsync CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'fuelsync'@'%' IDENTIFIED BY '<password>';
GRANT ALL PRIVILEGES ON fuelsync.* TO 'fuelsync'@'%';
FLUSH PRIVILEGES;
```

스키마와 시드를 올리려면 아래 순서로 실행합니다 (기존 동일 이름 테이블은 DROP 후 재생성).

```bash
mariadb -u root -p --default-character-set=utf8mb4 fuelsync < db/schema.sql
mariadb -u root -p --default-character-set=utf8mb4 fuelsync < db/seed.sql
```

운영 중 DB에서 **이력만 비우고 스키마는 유지**하려면 `db/purge_data_only.sql` 을 실행합니다. 사용자·탱크 등 기준 데이터는 그대로 남습니다.

이미 만들어 둔 DB를 최신 스키마로 올리려면 `db/migrations/` 의 파일을 번호 순서대로 적용합니다 (`ADD COLUMN IF NOT EXISTS` / `CREATE TABLE IF NOT EXISTS` 라 다시 실행해도 안전합니다).

### 2. 백엔드 실행

```bash
cd backend
mvn spring-boot:run
```

백엔드가 `http://localhost:8080` 에서 실행됩니다.

### 3. 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```

프론트엔드가 `http://localhost:5173` 에서 실행됩니다.

## API 엔드포인트

| Method | URL | 설명 |
|--------|-----|------|
| GET | /api/dashboard | 전체 현황 (탱크, 오늘 통계, 유종별 합산) |
| GET | /api/tanks | 탱크 목록 |
| GET | /api/tanks/{id} | 탱크 상세 |
| POST | /api/tanks | 탱크 등록 |
| PATCH | /api/tanks/{id}/sensor | 센서 데이터 업데이트 |
| GET | /api/transactions/recent | 최근 1시간 판매 이력 |
| POST | /api/transactions | 판매 등록 (재고 자동 차감) |
| GET | /api/stock-in | 입고 이력 |
| POST | /api/stock-in | 입고 등록 (재고 자동 추가) |
| POST | /api/adjustments | 일일 재고 보정 |
| POST | /api/price/simulate | 권장 판매가 시뮬레이션 |
| POST | /api/price/history | 가격 기록 |
| WS | /ws | WebSocket (STOMP) — /topic/stock/{tankId}, /topic/alert |

## DB ERD (논리 관계, 한글명)

FK로 묶인 관계는 실선으로 표현합니다. `tb_user`, `tb_station_info`, `tb_selling_price`, `tb_price_history` 는 DDL 상 다른 테이블을 참조하지 않는 독립 엔터티입니다.

```mermaid
erDiagram
  tb_tank["탱크 마스터 (tb_tank)"] ||--o{ tb_transaction["판매 (tb_transaction)"] : tank_id
  tb_tank ||--o{ tb_stock_in["입고 (tb_stock_in)"] : tank_id
  tb_tank ||--o{ tb_stock_adjustment["재고 조정 (tb_stock_adjustment)"] : tank_id
  tb_excel_upload_batch["엑셀 업로드 배치 (tb_excel_upload_batch)"] ||--o{ tb_excel_upload_row["엑셀 파싱 행 (tb_excel_upload_row)"] : batch_id

  tb_user["사용자 계정 (tb_user)"] {
    bigint user_id PK "앱 미연동 DDL만"
  }
  tb_station_info["주유소 기본 정보 (tb_station_info)"] {
    bigint station_id PK
  }
  tb_selling_price["유종별 판매가 이력 (tb_selling_price)"] {
    bigint price_id PK
  }
  tb_price_history["가격 이력 (tb_price_history)"] {
    bigint history_id PK
  }
```

## 테이블 요약 및 앱 연동 여부

| 한글 설명 | 테이블 | 백엔드(MyBatis) 사용 |
|-----------|--------|----------------------|
| 사용자(점주/직원) | tb_user | 미연동 — 스키마만 있음, `/api/auth/login` 미구현 |
| 탱크 마스터 | tb_tank | 사용 |
| 판매 | tb_transaction | 사용 |
| 입고 | tb_stock_in | 사용 |
| 재고 보정 | tb_stock_adjustment | 사용 |
| 자사·경쟁사 단가 이력 | tb_price_history | 사용 |
| 주유소 정보(주소·KATECH 등) | tb_station_info | 사용 |
| 유종별 판매가 시행일 이력 | tb_selling_price | 사용 |
| 엑셀 업로드 배치 메타 | tb_excel_upload_batch | 사용 |
| 엑셀 행 원본 스냅샷 | tb_excel_upload_row | 사용 |

## 화면 ↔ 소스 ↔ 테이블

라우트 기준: `frontend/src/App.tsx`.

| 화면(경로) | 주요 소스 | 참조 테이블(직접·집계) |
|------------|-----------|-------------------------|
| 대시보드 `/` | `frontend/src/pages/Dashboard.tsx`, `components/dashboard/*`, `hooks/useWebSocket.ts` | tb_tank, tb_transaction, tb_stock_in, tb_excel_upload_row |
| 입고 `/stock-in` | `frontend/src/pages/StockInPage.tsx` | tb_stock_in, tb_tank |
| 판매 `/sales` | `frontend/src/pages/SalesPage.tsx` | tb_transaction, tb_tank |
| 탱크 상세 `/tanks/:tankId` | `frontend/src/pages/TankDetailPage.tsx` | tb_tank, tb_transaction, tb_stock_in, tb_stock_adjustment |
| 재고 조정 `/adjustments` | `frontend/src/pages/AdjustmentPage.tsx` | tb_stock_adjustment, tb_tank |
| 업로드 이력 `/excel-uploads` | `frontend/src/pages/ExcelUploadLogPage.tsx` | tb_excel_upload_batch, tb_excel_upload_row |
| 정보 관리 `/my-info` | `frontend/src/pages/MyInfoPage.tsx` | tb_station_info, tb_selling_price |
| 유가 시뮬 `/price` | `frontend/src/pages/PriceSimulatorPage.tsx` (내비에 없음, URL 직접 접근) | tb_price_history, tb_selling_price, tb_station_info; 타임라인은 tb_stock_in·tb_transaction 집계 |
| 로그인 `/login` | `frontend/src/pages/LoginPage.tsx` | DB 미연동(토큰 UI만, SecurityConfig permitAll) |

**엑셀 업로드 공통:** 대시보드 위젯·판매 화면 등에서 업로드 시 `ExcelUploadService`가 `tb_transaction`, `tb_stock_in`, `tb_price_history`, `tb_stock_adjustment`, `tb_excel_upload_batch`, `tb_excel_upload_row` 및 탱크 조회(`tb_tank`)를 갱신합니다.
