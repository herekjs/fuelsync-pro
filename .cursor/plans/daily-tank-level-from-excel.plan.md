# 일별 마감(레벨) 재고 DB 저장 및 조회 개선

## 1. 역산이 맞는지 (질문에 대한 답)

네. [TankService.findDailyRemaining](d:\Project\fuelsync-pro\backend\src\main\java\com\fuelsync\domain\tank\service\TankService.java)은 다음 세 가지 일별 합계와 **`tb_tank.current_volume`(오늘 앵커)** 로 역산합니다.

- `TankMapper.findDailyStockInVolumeByTankId` → 입고
- `TankMapper.findDailySoldVolumeByTankId` → 판매
- `TankMapper.findDailyAdjustmentVolumeByTankId` → 재고조정(`diff_volume` 합계)

즉 **엑셀에서 이미 계산해 넣은 마감 값을 그대로 읽는 것이 아니라**, 운영 테이블 합계를 뒤에서부터 깎아 내려가며 `endVolume`을 만듭니다.

---

## 2. 요구사항 정리 (사용자 선택 반영)

- **범위**: 엑셀 업로드로 **해당 행이 실제 처리된 날·유종(`!skipGas` / `!skipDiesel`)** 에 대해서만 일별 **종료 재고(레벨)** 를 DB에 저장한다.
- **화면**: 가능하면 **저장된 스냅샷을 우선 표시**하고, 스냅샷이 없는 날은 기존 역산 로직으로 채운다 (차트 연속성 유지).
- 엑셀로 처리되지 않은 날은 역산 유지 (선택지 “excel_only”).

---

## 3. 설계 요약

### 3.1 신규 테이블 (예: `tb_tank_daily_level`)

| 컬럼 | 설명 |
|------|------|
| `tank_id`, `level_date` | 복합 유니크 |
| `end_volume_liters` | 해당 영업일 마감 시점 재고(L), 엑셀 처리 결과와 일치하도록 저장 |
| `source` | 예: `EXCEL` (향후 확장 시 `MANUAL` 등) |
| `updated_at` | 갱신 시각 |

스키마는 수동 관리 정책에 맞춰 [db/init.sql](d:\Project\fuelsync-pro\db\init.sql)(및 [backend/resources/db/init.sql](d:\Project\fuelsync-pro\backend\src\main\resources\db\init.sql) 동기화 여부는 프로젝트 관행대로)와 별도 `alter_*.sql` 로 추가.

### 3.2 쓰기 경로 — 엑셀 업로드

[ExcelUploadService](d:\Project\fuelsync-pro\backend\src\main\java\com\fuelsync\domain\transaction\service\ExcelUploadService.java) 메인 루프에서 **휘발유/경유 각각** 해당 일 행 처리가 끝난 직후(`skip`이 아닐 때만):

- 이미 메모리상 `gasTank` / `dieselTank` 가 입고·판매·(필요 시)엑셀 조정까지 반영된 상태이므로, **`tank.getCurrentVolume()`** 을 그대로 스냅샷 값으로 쓰면 **실제 장부 마감 재고**와 맞는다.
- `UPSERT` 로 `(tank_id, level_date)` 당 최신 값 유지 (동일 일자 재업로드 시 덮어씀).

`skipGas` / `skipDiesel`(증분 업로드에서 이미 엑셀 데이터 있는 일자)일 때는 **이번 업로드에서 스냅샷을 갱신하지 않음** → 기존 스냅샷 또는 역산 유지.

### 3.3 읽기 경로 — 일별 잔여량 API

`findDailyRemaining` 역산 루프를 다음처럼 조정:

1. 조회 구간 `[from, endDate]` 에 대해 `tb_tank_daily_level` 을 한 번에 로드해 `Map<LocalDate, BigDecimal>` 으로 둔다.
2. 기존과 같이 `today → from` 역순 루프를 돌되, 각 날짜 `cursor` 에 대해:
   - `closingForChain = snapshot(cursor).orElse(runningEnd)` — **스냅샷이 있으면 그 날 마감을 스냅샷으로 고정**
   - 화면에 내려주는 `endVolume` 은 `closingForChain`
   - 다음 날(더 과거)로 넘어갈 때 `runningEnd = closingForChain.subtract(netMovement(cursor))`

이렇게 하면 엑셀로 저장된 날은 **DB에 넣은 레벨 재고가 그대로 노출**되고, 스냅샷이 없는 날은 **기존 `current_volume` 앵커 기반 역산**으로 이어진다.

입고·판매·조정 **분해 칼럼**(`inVolume`, `soldVolume`, `adjustmentVolume`)은 지금처럼 동일 Mapper 집계를 유지해도 됨 (사용자 요청은 마감 레벨 저장·표시 중심).

### 3.4 운영 데이터 초기화 / 삭제

[OperationalDataPurgeService](d:\Project\fuelsync-pro\backend\src\main\java\com\fuelsync\domain\maintenance\service\OperationalDataPurgeService.java) 에서 업로드·운영 테이블 비울 때 **`tb_tank_daily_level` 도 함께 truncate** 하여 고아 스냅샷이 남지 않게 한다.

### 3.5 (선택, 후속) 덮어쓰기 삭제와 스냅샷

`incrementalOnly=false` 로 해당 일자 엑셀 데이터 삭제 후 재삽입 시, 같은 루프에서 UPSERT 가 다시 돌므로 일반적으로 일치한다. 만약 특정 일자가 파일에서 빠져 삭제만 되고 재삽입되지 않는 경우가 생기면, 그때는 해당 `(tank_id, date)` 스냅샷 삭제 규칙을 추가할지 검토 (초기 구현에서는 업로드 범위 안에서만 UPSERT 로 충분할 가능성이 큼).

---

## 4. 변경 파일(예상)

- DDL: `db/alter_tank_daily_level.sql` (+ init 동기화)
- `TankMapper.java` / `TankMapper.xml`: `upsertDailyLevel`, `findDailyLevelsByTankAndDateRange`
- `TankService.java`: `findDailyRemaining` 에 스냅샷 병합 로직
- `ExcelUploadService.java`: 행 처리 후 `upsertDailyLevel` 호출
- `OperationalDataPurgeMapper`(+ XML): `tb_tank_daily_level` truncate

프론트는 DTO 필드가 동일하면 **변경 불필요** (같은 API에서 `endVolume` 만 의미가 “스냅샷 우선”으로 달라짐).

---

## 5. 검증 시나리오

1. 1월 엑셀 업로드 후 특정 일자 `endVolume` 이 스냅샷과 역산 결과가 일치하는지 (현재 데이터 정합 시).
2. 2월만 증분 업로드 후 1월 스냅샷 행이 불변인지, 2월 일자는 스냅샷으로 고정되는지.
3. 동일 일자 재업로드(전체 덮어쓰기) 시 스냅샷 값 갱신 확인.
