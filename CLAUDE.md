# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

FuelSync Pro — gas-station fuel inventory / price management. Monorepo: `backend/` (Spring Boot 3.2.5, Java 17, MyBatis, MariaDB, STOMP WebSocket, Maven, packaged as **WAR**) and `frontend/` (React 18, TypeScript, Vite 5, Tailwind 3, ApexCharts). `db/` holds DDL (`init.sql` drops/recreates all `tb_*` tables + seed; `purge_data_only.sql` wipes data but keeps schema). The same two SQL files are duplicated in `backend/src/main/resources/db/` — keep them in sync. Docs (README.md, ARCHITECTURE.md, SCREEN_FEATURES.md) are written in Korean.

## Commands

Backend (run from `backend/`; DB must exist, see below):
```bash
mvn spring-boot:run                                   # http://localhost:8080
mvn test                                              # only test: ExcelUploadServiceTest
mvn test -Dtest=ExcelUploadServiceTest#methodName     # single test
mvn package                                           # also builds the frontend (see below)
```

Frontend (run from `frontend/`):
```bash
npm install
npm run dev      # http://localhost:5173, Vite proxies /api and /ws -> localhost:8080
npm run build    # tsc && vite build — the only type check/lint; no test runner or linter configured
```

Database: MariaDB, schema `fuelsync`; run `db/init.sql` to create tables and seed. `application.yml` connects as `root`/`1234` on localhost:3306.

`mvn package` runs `frontend-maven-plugin` (installs Node v20.15.1 into `backend/target/node`, runs `npm install` + `npm run build` in `../frontend`) and copies `frontend/dist` into the WAR's `static/`. `SpaWebConfig` serves the SPA from the backend in that case.

## Backend architecture

- Layout: `com.fuelsync.domain.<name>.{controller,service,mapper,model,dto}`; cross-cutting code in `config/`, `security/`, `common/`, `websocket/`. Domains: tank, transaction, stockin, adjustment, price (history + selling price), station, dashboard, excel (upload log), competitor, auth, user, maintenance (data purge).
- **MyBatis only, no JPA.** Interfaces are `@Mapper`; SQL lives in `src/main/resources/mapper/*Mapper.xml` (namespace = mapper interface FQN; `map-underscore-to-camel-case` on). There is **no dirty checking**: after mutating a model (e.g. `tank.deductVolume()` / `addVolume()`, which recalculate status) you must call `tankMapper.updateVolume(tank)` explicitly. Inserts need `useGeneratedKeys="true" keyProperty=...`. Complex aggregates return `resultType="map"` and are parsed in the service.
- Models are plain POJOs (`@Getter @Setter @NoArgsConstructor`) with domain logic (Tank state machine NORMAL/WARNING/DANGER by fill ratio vs thresholds). Services are `@Transactional` (`readOnly = true` for reads) and throw `FuelSyncException`; `GlobalExceptionHandler` converts to responses.
- Every REST response is `ResponseEntity<CommonResponse<T>>` (`CommonResponse.ok(...)` / `.fail(code, msg)`); controllers only delegate to services.
- Any change to tank volume must publish via `StockEventPublisher` (`publishStockUpdate`, plus `publishAlert` when status != NORMAL) → STOMP topics `/topic/stock/{tankId}` and `/topic/alert`, endpoint `/ws` (SockJS).
- `ExcelUploadService` (in `domain/transaction`) is the big cross-domain writer: an Excel upload (Apache POI) inserts transactions, stock-ins, price history and stock adjustments and records `tb_excel_upload_batch` / `tb_excel_upload_row` via `ExcelUploadLogService`.
- **Auth is now enforced** (some docs still say `permitAll`/unimplemented — they're stale): `SecurityConfig` is stateless JWT; `POST /api/auth/login` and `/ws/**` are open, `/api/**` requires auth. `JwtAuthenticationFilter`, `JwtTokenProvider`, `DbUserDetailsService` (backed by `tb_user`), and `StompJwtChannelInterceptor` for WebSocket. JWT secret, expiry, and CORS origins are under `fuelsync.*` in `application.yml`.

## Frontend architecture

- `src/App.tsx` holds the router and `NAV_ITEMS`; adding a page means updating both. Routes: `/login`, `/`, `/stock-in`, `/sales`, `/tanks/:tankId`, `/adjustments`, `/excel-uploads`, `/my-info`, `/price` (reachable by URL, not in nav).
- All HTTP goes through the `*Api` objects in `src/services/api.ts` (Axios; interceptor attaches the JWT from `localStorage`); never call axios directly. Responses are `CommonResponse<T>`, so data is at `res.data.data`. API/WS types live in `src/types/index.ts`.
- Page state is local `useState`/`useCallback` (zustand is installed but unused). Live stock updates come from the `useWebSocket` hook — don't create STOMP clients elsewhere.
- Tailwind classes only (no inline `style`). Tank status colors: NORMAL=green, WARNING=yellow, DANGER=red; brand color `blue-700`.
- ApexCharts: whenever `tooltip.shared: true` is set, also set `tooltip.intersect: false`, otherwise the chart throws on mount. Type-checking via `npm run build` isn't enough for chart/router/browser APIs — load the page in dev and check the console.

## Conventions from `.cursor/rules/`

Rules in `.cursor/rules/{project-overview,backend-spring,frontend-react}.mdc` are the source for the points above (note their auth/login remarks are outdated).
