# RECARRY Backend Design

Frontend v1(React + Vite)을 실제 운영 가능한 구조로 확장하기 위한 backend / database 설계.
결제 · 배송 API · 외부 알림은 아직 없다. 가격 · 재고 · 회수처 등 사업 값은 여전히 **sample** 이다.

---

## 1. Architecture

```
React + Vite (repo root)
   │  fetch — src/lib/api.ts   (dev: Vite proxy /api → :8080, prod: VITE_API_BASE_URL)
   ▼
Spring Boot 4 (backend/)       REST /api/*  ·  Spring Security + JWT (HS256)
   │  Spring Data JPA  ·  Flyway
   ▼
PostgreSQL 15+                 local: docker-compose 또는 로컬 클러스터
                               prod : Supabase PostgreSQL (DB_URL 만 바꾼다)
```

도메인별 패키지: `auth` `user` `carrier` `booking` `admin` `common` — 각각 controller / service / repository / entity / dto.

---

## 2. Frontend 가 필요로 하는 데이터 (역추적)

| 화면 | 필요한 값 | 출처 (v1 → v2) |
|---|---|---|
| Collection / Product | size, name, inch, grade, price, extra, capacity, usage, dims, weight, headline, desc | `carriers.ts` → `carrier_models` |
| Product 패널 / Passport / Story | carrier ID, 회수처, 수리 요약, 검수일, 타임라인 | `carriers.ts` → `carriers` + `carrier_events` |
| Booking Step 2 | 사이즈별 **해당 날짜에** 예약 가능한 대수 | `available`(고정값) → `GET /api/carriers/availability` |
| 가격 | 2박 3일 기본 + 추가 1박 | 프론트 계산 → **서버 재계산**, 프론트는 표시용 |
| Complete | 예약번호, 캐리어, 날짜, 합계 | history state → `POST /api/bookings` 응답 |
| 표시 전용 | 일러스트 색, 배경, SVG 크기, 사진 | 프론트에 남는다 (`data/presentation.ts`) |

---

## 3. Entity / 관계

```
carrier_models (size 20/24/28)      사이즈 단위 상품 정보·가격
   1 ─── N  carriers                캐리어 한 대 (Carrier ID, 등급, 운영 상태)
               1 ─── N  carrier_events    회수·수리·세척·검수·여행 기록 (Story / Timeline)
               1 ─── N  bookings  N ─── 1  users
```

- 가격은 **모델**에 있다 (같은 사이즈는 같은 요금). 예약 시점 금액은 `bookings` 에 스냅샷.
- 등급 · 회수처 · 수리 이력은 **캐리어 한 대**에 있다 (Carrier ID 가 브랜드 차별점).
- `carrier_inspections` / `carrier_repairs` 를 따로 두지 않고 `carrier_events.type` 하나로 묶었다
  (화면이 이미 하나의 타임라인으로 보여준다). 항목별 검수 결과 저장이 필요해지면 그때 분리한다.
- 주소는 예약마다 스냅샷으로 저장한다 (`addresses` 테이블 없음 — 주소록 기능이 생기면 추가).

---

## 4. 상태

### 캐리어 운영 상태 (`carriers.status`, 저장)
| 값 | 의미 |
|---|---|
| `AVAILABLE` | 렌탈 풀에 있음 |
| `INSPECTION` | 반납 후 세척·검수 중 |
| `REPAIR` | 수리 중 |
| `UNAVAILABLE` | 폐기·보류 |

### 캐리어 표시 상태 (API 응답 `status`, 계산)
`RESERVED` / `RENTED` 는 **날짜에 따라 달라지므로 저장하지 않는다.**
오늘 기준으로 `IN_USE` 예약이 걸려 있으면 `RENTED`, 오늘을 포함하는 `REQUESTED/CONFIRMED` 예약이 있으면 `RESERVED`,
아니면 운영 상태를 그대로 돌려준다.

### 예약 상태 (`bookings.status`)
```
REQUESTED ──admin──▶ CONFIRMED ──admin──▶ IN_USE ──admin──▶ RETURNED
    │                    │
    └──user/admin──▶ CANCELLED ◀──admin──┘
```
- 결제가 없으므로 생성 직후는 `REQUESTED` (예약 요청).
- 사용자는 `REQUESTED` 일 때만 취소할 수 있다. 환불은 없다 (정책 미정).
- **점유 상태**: `REQUESTED` `CONFIRMED` `IN_USE` — 이 상태의 예약만 재고를 막는다.

---

## 5. 재고 / 중복 예약 방지

- 점유 기간 = `[start_date - 1일, end_date]` (출발 전날 배송 ~ 반납일 회수, 양 끝 포함).
  세척·검수 버퍼는 정책 미정이라 넣지 않았다 → `recarry.booking` 설정으로 추가할 자리만 둔다.
- **DB**: `btree_gist` 배타 제약
  `EXCLUDE USING gist (carrier_id WITH =, occupied WITH &&) WHERE (status IN (점유 상태))`
  — 동시에 두 요청이 들어와도 DB 가 하나만 통과시킨다.
- **Service**: 사이즈의 `AVAILABLE` 캐리어 중 기간이 비어 있는 한 대를 고른다
  (요청이 특정 Carrier ID 를 원하면 그것부터). 행 잠금(`FOR UPDATE`) 후 겹침 확인 → 저장.
  그래도 제약 위반이 나면 409 `CARRIER_UNAVAILABLE`.
- 사이즈별 가능 대수: `GET /api/carriers/availability?start&end` 가 같은 규칙으로 계산.

---

## 6. 가격

`total = price + max(0, nights - 2) × extra_night_price` (v1 프론트와 같은 규칙, 값은 sample).
- 서버가 항상 다시 계산해 `base_price / extra_price / total_price` 로 저장.
- 요청의 `expectedTotal` 은 **검증용**일 뿐이다. 서버 금액과 다르면 409 `PRICE_MISMATCH`
  (사용자가 본 금액과 다른 금액으로 예약되지 않게).

---

## 7. DB schema (요약 — 원본은 `backend/src/main/resources/db/migration`)

| 테이블 | 주요 컬럼 |
|---|---|
| `users` | id, email(unique, 소문자), password_hash(BCrypt), name, phone, role(USER/ADMIN), created_at, updated_at |
| `carrier_models` | id, size(unique '20'/'24'/'28'), name, inch, capacity, usage, dims, weight, price, extra_night_price, headline, description |
| `carriers` | id, model_id FK, carrier_code(unique), grade(A/B), status, collected_from, repair_summary, inspected_at, featured |
| `carrier_events` | id, carrier_id FK, event_date, type, title, detail, sort_order |
| `bookings` | id, booking_number(unique), user_id FK, carrier_id FK, start_date, end_date, nights, occupied(생성 컬럼), recipient_name, phone, address, address_detail, request_message, base_price, extra_price, total_price, status, cancelled_at, created_at, updated_at |

제약: 가격 ≥ 0, `end_date > start_date`, enum 값 CHECK, 위 배타 제약.

---

## 8. API

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| POST | `/api/auth/signup` | public | 회원가입 → token |
| POST | `/api/auth/login` | public | 로그인 → token |
| GET | `/api/users/me` | USER | 내 정보 |
| GET | `/api/carriers` `?size=` | public | 사이즈별 상품 + 대표 캐리어 + 운영 가능 대수 |
| GET | `/api/carriers/{code}` | public | 캐리어 한 대 (Carrier ID) + 타임라인 |
| GET | `/api/carriers/availability` `?start&end` | public | 기간 내 사이즈별 예약 가능 대수 · 금액 |
| POST | `/api/bookings` | USER | 예약 요청 생성 |
| GET | `/api/bookings` | USER | 내 예약 목록 |
| GET | `/api/bookings/{bookingNumber}` | USER(본인) | 내 예약 상세 |
| POST | `/api/bookings/{bookingNumber}/cancel` | USER(본인) | `REQUESTED` 예약 취소 |
| GET | `/api/admin/carriers` | ADMIN | 전체 캐리어 + 표시 상태 |
| PATCH | `/api/admin/carriers/{id}/status` | ADMIN | 운영 상태 변경 |
| GET | `/api/admin/bookings` `?status=` | ADMIN | 전체 예약 |
| GET | `/api/admin/bookings/{bookingNumber}` | ADMIN | 예약 상세 |
| PATCH | `/api/admin/bookings/{bookingNumber}/status` | ADMIN | 예약 상태 변경 (전이 규칙 검사) |

에러 형식: `{ "code": "CARRIER_UNAVAILABLE", "message": "...", "fields": { ... } }`

---

## 9. 인증 / 권한

**선택: Spring Security + 자체 발급 JWT (HS256).** Supabase Auth 와 비교:

| | Supabase Auth | Spring Security JWT (선택) |
|---|---|---|
| 비즈니스 규칙 위치 | Spring 과 Supabase 둘로 나뉨 | Spring 한 곳 |
| 사용자 테이블 | `auth.users` + 프로필 테이블 동기화 | `users` 하나 |
| 로컬 개발 | Supabase 프로젝트 필요 | Postgres 만 있으면 됨 |
| 종속성 | supabase-js SDK, JWKS 검증 | Spring 표준 (oauth2-resource-server) |
| 소셜 로그인 | 쉬움 | 추후 추가 필요 |

Supabase 는 **PostgreSQL 호스팅으로만** 쓴다. 비밀번호는 Spring DB 에 BCrypt 로 한 번만 저장한다.

- 토큰: **httpOnly 쿠키** `recarry_session` (Path=/api, Secure, SameSite=`COOKIE_SAME_SITE`, 기본 Lax).
  본문·localStorage 에는 토큰이 없다 — 브라우저 JS 가 읽을 수 없다. 만료 기본 2시간 (`JWT_TTL_MINUTES`),
  claim `sub`=user id, `roles`. (`Authorization: Bearer` 헤더도 받는다 — API 도구용)
- `GET /api/auth/session`: 로그인 여부. 비로그인·만료·위조 쿠키면 401 대신 `{user:null}` 을 주고 나쁜 쿠키는 지운다.
- 공개 API(`/api/carriers*`, 로그인·가입·로그아웃)는 쿠키를 보지 않는다 — 만료 쿠키가 남아도 카탈로그가 열린다.
- 로그아웃: `POST /api/auth/logout` 이 쿠키를 지운다. 서버 세션이 없으므로 이미 복사된 토큰은 만료까지 유효하다.
- CSRF: 쿠키는 자동으로 붙으므로, 상태를 바꾸는 `/api` 요청은 `X-Recarry-Client` 헤더가 있어야 통과한다
  (다른 사이트의 form 은 헤더를 못 붙이고, fetch 는 CORS preflight 에서 막힌다). SameSite=Lax 가 한 겹 더 막는다.
- 로그인 실패 제한: 15분 창에서 이메일당 5회 · IP당 30회 실패하면 429 `TOO_MANY_ATTEMPTS`.
  인스턴스 메모리 기준 — 서버를 여러 대로 늘리면 공유 저장소(DB/Redis)로 옮긴다.
  프록시 뒤에서는 실제 IP 를 받도록 호스팅의 forwarded 헤더 설정을 확인한다 (`server.forward-headers-strategy`).
- `/api/admin/**` 는 ADMIN 만. 예약 조회·취소는 service 에서 **소유자 확인** (다른 사람 예약은 404 로 숨긴다).
- 첫 관리자: `ADMIN_EMAIL` + `ADMIN_PASSWORD` 환경변수가 있으면 기동 시 생성/승격 (코드에 비밀번호 없음).

---

## 10. 환경 변수

| 변수 | 쓰는 곳 | 예 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | backend | 로컬 `local` · 운영 `prod` (`dbcheck` 를 더하면 연결 점검만) |
| `RECARRY_ENV_FILE` | backend | 읽을 환경 파일. 기본 `.env`, 운영 DB 점검은 `.env.prod` |
| `DB_URL` `DB_USERNAME` `DB_PASSWORD` | backend | 로컬 `jdbc:postgresql://localhost:5433/recarry` · 운영 Supabase pooler (`sslmode=require`) |
| `DB_POOL_SIZE` | backend | `5` (Supabase pooler 연결 한도가 작다) |
| `JWT_SECRET` | backend | 32바이트 이상 임의 문자열 (없으면 기동 실패). 로컬·운영 값 분리 |
| `JWT_TTL_MINUTES` | backend | `120` |
| `CORS_ALLOWED_ORIGINS` | backend | 운영 프론트 origin. localhost 를 넣지 않는다 |
| `COOKIE_SAME_SITE` | backend | 같은 사이트 `Lax`, 다른 사이트 `None` |
| `COOKIE_SECURE` | backend | 기본 `true`. `local` 프로필만 `false` (http://localhost). prod 에서 false 면 기동 거부 |
| `ADMIN_EMAIL` `ADMIN_PASSWORD` | backend (선택) | 첫 관리자 |
| `PORT` | backend | 호스팅이 주는 포트 (기본 8080) |
| `FORWARD_HEADERS_STRATEGY` | backend (prod) | 기본 `native` — 사설 IP 프록시의 X-Forwarded-* 만 신뢰 |
| `LOGIN_MAX_FAILURES_PER_IP` | backend | 기본 30. 프록시 뒤라 IP 가 모이면 올린다 |
| `VITE_API_BASE_URL` | frontend build | 비우면 같은 origin `/api` (권장: Vercel rewrite — 이때도 `CORS_ALLOWED_ORIGINS` 필요) |

로컬은 `backend/.env`, 운영 DB 점검은 `backend/.env.prod` (둘 다 gitignore). 예시는 `.env.example` · `.env.prod.example`.
운영 서버는 파일 없이 호스팅의 환경변수만 쓴다.

### 프로필

| 프로필 | DB | Flyway 위치 | 쿠키 | 비고 |
|---|---|---|---|---|
| `local` | 로컬 5433 | migration + **sample** | Secure 끔 | 개발용 |
| `prod` | Supabase | migration **만** | Secure | `ProductionGuard` 가 기동 전 검사 |
| `dbcheck` | (함께 준 프로필) | 실행 안 함 | — | 웹 서버 없이 연결·스키마 상태만 출력 |

`ProductionGuard` (prod): local 프로필 동시 사용 · 로컬 DB 주소 · `sslmode=require` 없음 · sample 경로 · Secure 가 아닌 쿠키 중
하나라도 있으면 migration 전에 기동을 멈춘다. JPA 는 항상 `ddl-auto: validate` — 스키마는 Flyway 만 바꾼다.

---

## 11. Migration / Seed

- `db/migration/V1__schema.sql` — 스키마. 운영에도 적용.
- `db/migration/V2__lock_down_supabase_data_api.sql` — 모든 테이블 RLS + Supabase `anon`·`authenticated` 권한 회수
  (Supabase 는 public 스키마를 Data API 로 공개하고 새 테이블에 두 역할의 전체 권한을 준다). 해당 역할이 없는 로컬 DB 에서는 권한 회수를 건너뛴다.
  `flyway_schema_history` 는 migration 중 Flyway 가 잠그고 있어 prod 기동 시 `SupabaseLockdown` 이 migration 뒤에 처리한다.
  **새 테이블을 만드는 migration 은 `ENABLE ROW LEVEL SECURITY` 를 함께 넣는다.**
- `backend/db/demo/demo_catalog.sql` — 운영 DB 시연용 **DEMO** 카탈로그. migration 이 아니며(classpath 밖) 운영자가 한 번 실행한다.
  sample 과 같은 값, 캐리어 ID 는 모두 `RC-<사이즈>-S<번호>`. 교체·제거 절차는 `backend/README.md` §5.
- `db/sample/R__sample_catalog.sql` — **sample** 카탈로그 (v1 `carriers.ts` 값). `local` 프로필에서만 적용.
  운영(prod)은 위치 자체를 빼고, guard 가 한 번 더 막는다. 운영 DB 는 빈 카탈로그로 시작한다 (§15).
- prod 는 `baseline-on-migrate: false` · `clean-disabled: true` — 이력 없는 기존 스키마를 덮어쓰거나 지우지 않는다.

---

## 12. Frontend ↔ Backend 흐름

```
앱 시작       ── GET /api/auth/session ────────▶ 로그인 여부 (쿠키)
Home/Product  ── GET /api/carriers ─────────────▶ 사이즈별 상품 (캐시)
Product 날짜 선택 → 예약하기 → (로그인 필요 시 /login → 원래 화면으로 복귀)
Booking Step 2 ── GET /api/carriers/availability ▶ 날짜별 가능 대수
Booking Step 3 ── (내 정보로 받는 분·연락처 미리 채움)
Booking Step 4 ── POST /api/bookings {size, carrierCode?, dates, 배송정보, expectedTotal}
               ◀─ 201 {bookingNumber, carrierCode, total, status: REQUESTED}
Complete      ── 응답 표시 · My Page 로 이동
My Page       ── GET /api/bookings, GET /api/bookings/{no}, POST /api/auth/logout
Admin         ── /api/admin/*
```

---

## 13. 테스트

- `backend/src/test/.../ApiIntegrationTest` — 실제 HTTP → Security → Service → PostgreSQL 전체 경로 17개 시나리오
  (회원가입·로그인, 입력 검증, 비인증·위조·만료 토큰, httpOnly 쿠키·로그아웃, CSRF 헤더, 로그인 실패 제한,
  다른 사용자 예약 접근, 없는 예약, 가격 조작, 중복 예약, 동시 예약 경합, 잘못된 날짜·캐리어, 사용자 취소,
  USER 의 관리자 API 접근, 관리자 상태 전이).
- `ProductionGuardTest` — prod 에서 위험한 설정 6가지를 거부하는지.
- 실제 PostgreSQL 이 필요하다 (`TEST_DB_*`). 테스트 DB 는 매번 비우고 다시 migrate 한다 — 이름에 `test` 가 없으면 거부.
- Frontend 는 `npm run build` (tsc 포함). 화면 흐름은 dev 서버 + backend 를 띄우고 확인한다.

## 14. 배포

```
Vercel (frontend)  ──/api/* rewrite──▶  Spring Boot (호스팅 미정, prod)  ──SSL──▶  Supabase PostgreSQL
```

- 권장: Vercel rewrite 로 `/api/*` 를 backend 로 넘겨 **같은 origin** 으로 만든다 → 1st-party 쿠키, SameSite=Lax.
  단 rewrite 는 Host 를 backend 로 바꾸고 Origin(프론트 도메인)은 그대로라 backend 는 cross-origin 으로 본다 →
  `CORS_ALLOWED_ORIGINS` 에 프론트 도메인을 넣어야 로그인·예약(POST)이 통과한다. 목록에 없는 origin 은 403.
  (프론트가 backend 를 직접 부르면 `VITE_API_BASE_URL` + `CORS_ALLOWED_ORIGINS` + `COOKIE_SAME_SITE=None`,
  이때 Safari 등은 3rd-party 쿠키를 막을 수 있다.)
- 운영 backend 에는 `local` 프로필을 켜지 않는다 — 켜면 guard 가 기동을 거부한다.
- 절차는 `backend/README.md` §3–5, 배포 전체는 `DEPLOYMENT.md`. health check: `GET /api/health`.

**현재 상태 (2026-09-25)**: Supabase(PostgreSQL 17.6, session pooler, TLS 1.3) 에 V1 · V2 적용,
Data API 차단 확인(anon · authenticated 권한 없음, 전 테이블 RLS), demo 카탈로그 입력(모델 3 · 캐리어 6 · 이벤트 15),
prod 프로필 backend + 프론트로 E2E 확인. backend 호스팅 · Vercel 도메인은 아직 정해지지 않았다.

## 15. 운영 캐리어 등록 (설계 — 아직 구현하지 않음)

운영 DB 에는 지금 demo 카탈로그만 있다 (§11). 실제 운영 데이터는 임의로 만들지 않는다.
현재 관리자 기능(상태 변경)은 그대로 두고, 등록이 필요해지면 아래를 추가한다.

| API (ADMIN) | 내용 |
|---|---|
| `POST /api/admin/carrier-models` | 사이즈 상품: size · name · inch · capacity · usage · dims · weight · price · extraNightPrice · headline · description |
| `PATCH /api/admin/carrier-models/{size}` | 가격·문구 수정 (기존 예약은 금액 스냅샷이라 영향 없음) |
| `POST /api/admin/carriers` | 캐리어 한 대: size · carrierCode(unique) · grade · collectedFrom · repairSummary · inspectedAt — 상태는 `INSPECTION` 으로 시작, 검수 후 `AVAILABLE` |
| `POST /api/admin/carriers/{id}/events` | 회수·수리·세척·검수·여행 기록 추가 |

- 테이블은 이미 있다 (스키마 변경 없음). 검증: 코드 형식, 사이즈 존재, 등급 A/B, 가격 ≥ 0.
- 그 전까지는 운영자가 검토한 SQL 을 Supabase SQL Editor 에서 실행하거나 versioned migration(`V2__...`) 으로 넣는다.
  sample 파일을 운영에 재사용하지 않는다.
