# FuelSync Pro 화면별 기능 정리

프론트엔드 라우트 기준으로 각 화면의 핵심 기능을 정리한 문서입니다.

## 화면 목록

| 경로 | 화면명 | 메뉴 노출 |
|---|---|---|
| `/login` | 로그인 | 미노출 |
| `/` | 대시보드 | 노출 |
| `/stock-in` | 입고 관리 | 노출 |
| `/sales` | 판매 관리 | 노출 |
| `/tanks/:tankId` | 탱크 상세 | 미노출 |
| `/adjustments` | 재고 조정 | 노출 |
| `/price` | 유가 이력/시뮬레이터 | 노출 |
| `/my-info` | 내 정보 관리 | 노출 |

---

## `/login` 로그인

- 주요 기능
  - 아이디/비밀번호 로그인 처리
  - 로그인 성공 시 토큰 저장 후 메인 화면 이동
- 입력/필터
  - `username`, `password` (필수)
- 주요 API 연동
  - `authApi.login`
- 예외/검증/특이사항
  - 실패 시 서버 메시지 우선 노출, 없으면 기본 오류 문구 표시
  - 로그인 화면은 헤더 없이 독립 레이아웃으로 렌더링

## `/` 대시보드

- 주요 기능
  - 오늘 매출/판매량/마진 요약 표시
  - 탱크 상태 게이지/리스트 표시
  - 유종별 통합 재고 표시
  - 최근 1시간 판매 추이 차트 표시
- 입력/필터
  - 조회형 화면(직접 입력 없음)
  - 탱크 카드 클릭 시 탱크 상세 화면 이동
- 주요 API 연동
  - `dashboardApi.get`
  - `transactionApi.getRecent`
  - `useWebSocket` (실시간 재고 업데이트 반영)
- 예외/검증/특이사항
  - 초기 로딩 상태 표시

## `/stock-in` 입고 관리

- 주요 기능
  - 입고 등록
  - 기간별 입고 이력 조회
- 입력/필터
  - 등록: 탱크, 입고량, 매입단가, 공급사, 입고일, 메모
  - 조회: 기간(`from`/`to`)
- 주요 API 연동
  - `tankApi.getAll`
  - `stockInApi.getByPeriod`
  - `stockInApi.create`
- 예외/검증/특이사항
  - 수치 입력 최소값 검증(`min=0.01`)
  - 등록 성공 시 목록 재조회 및 폼 초기화
  - 등록 실패 시 오류 메시지 표시

## `/sales` 판매 관리

- 주요 기능
  - 판매 등록
  - 기간별 판매 이력 조회
  - 조회 결과 총 판매량/총 매출 집계 표시
- 입력/필터
  - 등록: 탱크, 판매량, 단가, 결제수단, 승인번호
  - 조회: 시작일/종료일
- 주요 API 연동
  - `tankApi.getAll`
  - `transactionApi.create`
  - `transactionApi.getByPeriod`
- 예외/검증/특이사항
  - 판매량*단가 기반 예상 판매금액 표시
  - 날짜 필터 조회 시 일 단위 범위(00:00:00~23:59:59)로 변환
  - 결과가 없을 때 안내 메시지 표시

## `/tanks/:tankId` 탱크 상세

- 주요 기능
  - 단일 탱크 상세 정보 조회
  - 탱크별 입고 이력 조회
  - 이전 화면 이동
- 입력/필터
  - URL 파라미터 `tankId` 기반 조회
- 주요 API 연동
  - `tankApi.getOne`
  - `stockInApi.getByTank`
  - `useWebSocket` (실시간 재고 반영)
- 예외/검증/특이사항
  - 로딩/오류 상태를 별도로 표시
  - 탱크 상태에 따라 시각적 상태 색상 변경

## `/adjustments` 재고 조정

- 주요 기능
  - 재고 조정 등록
  - 기간별 조정 이력 조회
  - 차이량/차이금액 표시
- 입력/필터
  - 등록: 탱크, 장부재고, 실측재고, 조정일, 메모
  - 조회: 시작일/종료일
- 주요 API 연동
  - `tankApi.getAll`
  - `adjustmentApi.create`
  - `adjustmentApi.getByPeriod`
- 예외/검증/특이사항
  - 차이량(실측-장부) 즉시 계산
  - 동일 탱크/동일 일자 중복 조정 등록 제한 안내
  - 등록 실패 시 오류 메시지 표시

## `/price` 유가 이력/시뮬레이터

- 주요 기능
  - 유종별 가격 이력(차트/테이블) 조회
  - 권장 판매가 시뮬레이션
  - 반경 기반 경쟁사 평균가 조회
- 입력/필터
  - 이력 조회: 유종, 시작일, 종료일
  - 시뮬레이션: 유종, 평균매입가, 목표마진율, 경쟁사가
  - 경쟁사 조회 반경: 1~5km
- 주요 API 연동
  - `priceApi.getTimeline`
  - `priceApi.simulate`
  - `competitorApi.getAround`
- 예외/검증/특이사항
  - 이력 데이터가 없을 때 안내 메시지 표시
  - 경쟁사 데이터가 없을 때 대체 안내 표시

## `/my-info` 내 정보 관리

- 주요 기능
  - 탭 기반 운영 정보 관리
    - 탱크 정보 조회
    - 유종별 판매가 저장
    - 주유소 기본정보/좌표 저장
    - 경쟁사 판매가 조회/비교
- 입력/필터
  - 판매가 입력
  - 주유소 정보(기본정보, KATECH 좌표)
  - 경쟁사 조회 반경(1~10km)
- 주요 API 연동
  - `tankApi.getAll`
  - `sellingPriceApi.getAll`, `sellingPriceApi.upsert`
  - `stationInfoApi.get`, `stationInfoApi.update`
  - `competitorApi.getAround`
- 예외/검증/특이사항
  - 판매가 저장 시 양수 검증
  - 저장 성공/실패 메시지 표시
  - 경쟁사 항목 클릭 시 카카오맵 검색 새 창 열기

---

## 네비게이션/라우트 참고

- 메뉴 노출 라우트
  - `/`, `/stock-in`, `/sales`, `/adjustments`, `/price`, `/my-info`
- 히든 라우트(메뉴 미노출)
  - `/login`, `/tanks/:tankId`
- 기타
  - 정의되지 않은 경로(`*`)는 `/`로 리다이렉트

## 참고 소스

- `frontend/src/App.tsx`
- `frontend/src/pages/LoginPage.tsx`
- `frontend/src/pages/Dashboard.tsx`
- `frontend/src/pages/StockInPage.tsx`
- `frontend/src/pages/SalesPage.tsx`
- `frontend/src/pages/TankDetailPage.tsx`
- `frontend/src/pages/AdjustmentPage.tsx`
- `frontend/src/pages/PriceSimulatorPage.tsx`
- `frontend/src/pages/MyInfoPage.tsx`
- `frontend/src/services/api.ts`
