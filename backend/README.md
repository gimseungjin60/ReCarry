# RECARRY backend

Spring Boot 4 · Java 17 · PostgreSQL (로컬 / Supabase) · Flyway · Spring Security (JWT, httpOnly 쿠키).
설계: [`docs/BACKEND.md`](../docs/BACKEND.md)

## 1. 프로필과 환경 파일

| 실행 | 프로필 | 읽는 설정 | DB | sample 카탈로그 |
|---|---|---|---|---|
| 로컬 개발 | `local` | `backend/.env` | 로컬 PostgreSQL (5433) | 들어간다 |
| 운영 DB 점검 (로컬에서) | `prod` | `backend/.env.prod` | Supabase | **안 들어간다** |
| 운영 서버 | `prod` | OS 환경변수만 | Supabase | **안 들어간다** |
| 연결 점검만 | `prod,dbcheck` | 위와 같음 | 읽기만 | — |

- 환경 파일은 `RECARRY_ENV_FILE` 로 고른다 (기본 `.env`). `.env.prod` 로 실행하면 `.env` 는 **읽지 않는다**.
- `.env`, `.env.prod` 등 실제 값이 든 파일은 git 에 올라가지 않는다. 예시는 `.env.example`, `.env.prod.example`.
- `prod` 로 뜰 때 `ProductionGuard` 가 migration **전에** 검사하고 하나라도 걸리면 기동하지 않는다:
  `local` 프로필 동시 사용 · 로컬 DB 주소 · `sslmode=require` 없음 · sample 경로 · Secure 가 아닌 쿠키.
- `JWT_SECRET` 이 없거나 32바이트보다 짧으면 기동하지 않는다.

## 2. 로컬 개발

```bash
cp .env.example .env                               # 값 채우기
docker compose --env-file backend/.env up -d       # (repo 루트) Docker 가 되면
# Docker 없이: 설치된 PostgreSQL 15+ 로 전용 클러스터
initdb -D backend/.localdb -U recarry -A scram-sha-256 -E UTF8 --pwprompt
pg_ctl -D backend/.localdb -o "-p 5433 -c listen_addresses=localhost" -l backend/.localdb/server.log start
psql -h localhost -p 5433 -U recarry -d postgres -c "CREATE DATABASE recarry;" -c "CREATE DATABASE recarry_test;"

./gradlew bootRun                                  # http://localhost:8080
./gradlew clean build                              # 컴파일 + 테스트 (TEST_DB_* 필요)
```

프론트는 repo 루트에서 `npm run dev` — Vite 가 `/api` 를 `localhost:8080` 으로 넘긴다.

## 3. Supabase (운영) 연결

1. `backend/.env.prod` 를 연다 (없으면 `.env.prod.example` 을 복사). `DB_PASSWORD=` 뒤에 **Supabase DB 비밀번호를 직접** 넣는다.
2. **연결만 점검** (migration 안 함, DB 에 아무것도 쓰지 않음):
   ```bash
   RECARRY_ENV_FILE=.env.prod SPRING_PROFILES_ACTIVE=prod,dbcheck ./gradlew bootRun
   ```
   PowerShell: `$env:RECARRY_ENV_FILE='.env.prod'; $env:SPRING_PROFILES_ACTIVE='prod,dbcheck'; ./gradlew bootRun`
   → `[dbcheck]` 줄에 접속 사용자 · SSL · btree_gist · public 테이블 · Flyway 이력이 나온다.
3. **migration 적용 + 서버 실행**:
   ```bash
   RECARRY_ENV_FILE=.env.prod ./gradlew bootRun
   ```
   `V1__schema` · `V2__lock_down_supabase_data_api` 가 적용된다 (`db/sample` 은 prod 에서 제외 — guard 가 한 번 더 막는다).
   public 스키마에 이력 없는 테이블이 이미 있으면 Flyway 가 멈춘다 (`baseline-on-migrate: false`) — 조용히 덮어쓰지 않는다.
4. Supabase Dashboard → Table Editor 에서 `users · carrier_models · carriers · carrier_events · bookings · flyway_schema_history` 확인.

Supabase 는 PostgreSQL 호스팅으로만 쓴다 (인증은 이 backend 의 JWT). Session pooler 는 연결 수가 적으므로 `DB_POOL_SIZE` 를 작게 둔다.

**Supabase Data API 차단**: Supabase 는 public 스키마를 Data API(anon 키)로 자동 공개하고, 새 테이블에 anon · authenticated 전체 권한을 준다.
`V2` 가 우리 테이블에 RLS 를 켜고 두 역할의 권한을 회수한다. Flyway 이력 테이블은 migration 중 잠겨 있어
prod 기동 시 `SupabaseLockdown` 이 migration 뒤에 처리한다. **새 테이블 migration 에는 `ENABLE ROW LEVEL SECURITY` 를 꼭 넣는다.**
backend 는 테이블 소유자(postgres)로 접속하므로 RLS 의 영향을 받지 않는다.

**현재 상태 (2026-09-25)**: Supabase 에 V1 · V2 적용 완료, demo 카탈로그 입력 완료(§5), E2E 확인 완료.

## 4. 운영 서버 배포 (호스팅 미정)

어느 서비스든 **Java 17 + 환경변수**만 있으면 된다. 전체 절차 · 체크리스트: [`docs/DEPLOYMENT.md`](../docs/DEPLOYMENT.md)
**선택된 호스팅: Render** — 저장소 루트 `render.yaml` (Blueprint), 절차는 `docs/DEPLOYMENT.md` §11.

```bash
./gradlew bootJar                            # build/libs/recarry-backend.jar (실행 jar 하나만 만든다)
java -jar build/libs/recarry-backend.jar     # 환경변수는 호스팅 설정에서 준다 (.env 파일 없이), 포트는 PORT
docker build -t recarry-backend .            # 컨테이너 호스팅이면 (Dockerfile · .dockerignore 포함)
```

Health check: `GET /api/health` (인증 불필요, DB 확인 포함).

| 변수 | 운영 값 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` `DB_USERNAME` `DB_PASSWORD` | Supabase session pooler |
| `JWT_SECRET` | 새로 만든 32바이트 이상 값 |
| `CORS_ALLOWED_ORIGINS` | 프론트 origin (예: `https://recarry.vercel.app`) — rewrite 구성에서도 필요 |
| `COOKIE_SAME_SITE` | 프론트와 같은 사이트면 `Lax`, 다른 사이트면 `None` |
| `ADMIN_EMAIL` `ADMIN_PASSWORD` | 첫 관리자 생성 시에만 |
| `PORT` · `FORWARD_HEADERS_STRATEGY` · `LOGIN_MAX_FAILURES_PER_IP` | 선택 — `docs/DEPLOYMENT.md` §3 |

**권장 구성 — 같은 origin**: Vercel 의 rewrite 로 `/api/*` 를 backend 로 넘기면 브라우저 입장에서 API 가 프론트와 같은 origin 이 된다.
쿠키가 1st-party 라 `SameSite=Lax` 로 충분하고, Safari 등의 3rd-party 쿠키 차단에 걸리지 않으며, CORS 도 필요 없다.

```jsonc
// vercel.json — backend 주소가 정해지면 SPA rewrite 앞에 추가 (Vercel rewrite 는 환경변수를 못 쓴다)
{ "rewrites": [
  { "source": "/api/(.*)", "destination": "https://<backend-host>/api/$1" },
  { "source": "/(.*)", "destination": "/" }
] }
```

이 경우 프론트의 `VITE_API_BASE_URL` 은 비운다. 단 rewrite 는 Host 를 backend 로 바꾸고 브라우저의 Origin(프론트 도메인)은 그대로라
backend 입장에서는 cross-origin 요청이다 → **`CORS_ALLOWED_ORIGINS` 에 Vercel 도메인을 꼭 넣는다** (없으면 로그인·예약이 403).
로컬 Vite proxy 는 `changeOrigin: false` 로 Host 를 유지하므로 localhost 를 넣을 필요가 없다.

프론트가 backend 를 직접 부르게 하려면
`VITE_API_BASE_URL=https://<backend-host>` + `CORS_ALLOWED_ORIGINS` + `COOKIE_SAME_SITE=None` 이 필요하다
(이때 Safari 는 3rd-party 쿠키를 막을 수 있다).

## 5. Demo 카탈로그 (운영 DB 시연용)

`backend/db/demo/demo_catalog.sql` — **DEMO / SAMPLE DATA, 실제 운영 데이터가 아님**.
Flyway migration 이 아니다 (classpath 밖) — 자동으로 실행되지 않고 운영자가 직접 한 번 실행한다. 여러 번 실행해도 같다.

```bash
psql "host=<pooler host> port=5432 dbname=postgres user=<DB_USERNAME> sslmode=require" -v ON_ERROR_STOP=1 -f db/demo/demo_catalog.sql
# 또는 Supabase Dashboard → SQL Editor 에 파일 내용을 붙여넣기
```

- 값은 로컬 sample(`db/sample/R__sample_catalog.sql`)과 같다 (가격 · 등급 · 검수 · 수리 · Story).
- demo 캐리어는 모두 `S` ID 를 쓴다: `RC-20-S01~S03`, `RC-24-S01~S02`, `RC-28-S01` (`S01` 이 사이즈 대표).
  찾기: `carrier_code ~ '^RC-[0-9]{2}-S[0-9]{2}$'`
- 이미 있는 행은 건드리지 않는다 (운영에서 고친 가격·문구를 덮어쓰지 않는다).

**실제 운영 데이터로 바꿀 때 (자동화하지 않는다 — 운영자가 확인하며 직접)**

1. 실제 캐리어를 등록한다 (관리자 기능이 생기기 전에는 검토한 SQL 로). 사이즈 대표는 `featured` 로 지정.
2. `carrier_models` 의 가격·문구는 확정된 값으로 **수정**한다 (행을 지우지 않는다 — 실제 캐리어가 참조한다).
3. demo 캐리어는 삭제보다 **운영 중지**를 먼저 쓴다: 관리자 화면에서 `UNAVAILABLE` 로 바꾸면 예약·목록에서 빠진다.
4. 완전히 지워야 하면, 그 캐리어를 참조하는 예약이 없는지 확인한 뒤 백업을 떠 두고 수동으로 지운다.

**E2E 테스트 흔적**: 검증에 쓴 계정은 모두 `@test.recarry` 이메일이고 (관리자 `e2e-admin@test.recarry` 포함),
그 계정의 예약은 전부 `CANCELLED` 다. 실제 관리자를 만든 뒤(`ADMIN_EMAIL`) 테스트 계정은 정리 여부를 운영자가 판단한다.
