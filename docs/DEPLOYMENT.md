# RECARRY Deployment

Vercel(frontend) + Spring Boot(backend, 호스팅 미정) + Supabase PostgreSQL.
설계: [`BACKEND.md`](BACKEND.md) · backend 실행: [`../backend/README.md`](../backend/README.md)

---

## 1. 구조

```
브라우저 ──https──▶ Vercel (정적 프론트)
                     └─ /api/*  rewrite ──https──▶ Spring Boot (prod)  ──TLS──▶ Supabase PostgreSQL
                                                 (호스팅 미정)                  (session pooler, V1·V2 적용)
```

- 브라우저 입장에서 API 가 프론트와 **같은 origin** 이다 → 세션 쿠키가 1st-party (SameSite=Lax 로 충분, Safari 3rd-party 차단 영향 없음).
- 그래도 rewrite 는 Host 를 backend 로 바꾸고 Origin(프론트 도메인)은 그대로 보내므로 backend 는 cross-origin 으로 본다
  → `CORS_ALLOWED_ORIGINS` 에 Vercel 도메인이 **꼭** 있어야 한다.
- 프론트의 API 경로(`/api/*`)는 개발(Vite proxy)과 운영(Vercel rewrite)에서 같다. `VITE_API_BASE_URL` 은 비운다.

## 2. Backend 호스팅 조건 (provider 중립)

| 항목 | 값 |
|---|---|
| 런타임 | Java 17 (또는 `backend/Dockerfile` 로 컨테이너) |
| 빌드 | `cd backend && ./gradlew bootJar` → `build/libs/recarry-backend.jar` (테스트는 CI 에서) |
| 시작 | `java -jar build/libs/recarry-backend.jar` (환경변수는 호스팅 설정으로 — 파일 없이) |
| 포트 | `PORT` 환경변수 (없으면 8080) |
| Health check | `GET /api/health` → 200 `{"status":"UP"}` (DB `select 1` 포함, 실패 시 503). 인증 불필요 |
| HTTPS | 호스팅이 TLS 를 종료하고 앱에는 http 로 넘긴다 — prod 는 `X-Forwarded-*` 를 사설 IP 프록시에서만 믿는다 |
| 메모리 | 512MB 이상. 컨테이너는 `-XX:MaxRAMPercentage=60` (heap ~300MB) 이 들어 있다 — 같은 heap 으로 API 부하를 줬을 때 프로세스 최대 342MB. jar 로 직접 띄우면 `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60` |
| 종료 | graceful — SIGTERM 을 받으면 새 요청은 거절하고 처리 중인 요청을 최대 20초 동안 마친 뒤 종료 |
| 인스턴스 | 1대 권장 — 로그인 실패 제한이 인스턴스 메모리 기준 |
| 아웃바운드 | Supabase pooler `aws-0-ap-northeast-2.pooler.supabase.com:5432` 로 나갈 수 있어야 한다 |

기동 시 순서: Flyway(`db/migration` 만) → `ProductionGuard` 검사(실패하면 기동 거부, `CORS_ALLOWED_ORIGINS` 가 비면 경고) → 서버 시작 → `SupabaseLockdown` → (ADMIN_* 가 있으면) 관리자 생성.
로컬 PC 에서는 Docker 가 동작하지 않아(WSL2 가상화 미활성) `Dockerfile` 은 이미지로 빌드해 보지 못했다 — 첫 배포의 빌드 로그로 확인한다.

## 2a. 호스팅 후보 (결정 전 비교 — 2026-09 공식 문서 기준, 가격은 가입 시 다시 확인)

RECARRY 에 특히 중요한 차이는 **① 지역** (Supabase 가 서울 `ap-northeast-2` — 요청마다 DB 왕복이 여러 번이라 backend 가 멀면 매 요청이 느려진다),
**② 잠자기/콜드 스타트** (Spring Boot 는 기동에 수 초~수십 초가 걸린다), **③ 인스턴스 1대 유지** (로그인 실패 제한이 메모리 기준)다.

| | Render | Railway | Google Cloud Run |
|---|---|---|---|
| 배포 방식 | GitHub 연결 + **Dockerfile** (Java 네이티브 런타임 없음) | GitHub 연결 + Dockerfile 또는 자동 감지 | 컨테이너 이미지(Dockerfile) 또는 소스 배포, GitHub 연동은 Cloud Build |
| 난이도 | 낮음 | 낮음 | 중간 (GCP 프로젝트 · 결제 계정 · IAM) |
| 가장 가까운 지역 | 싱가포르 | 싱가포르 | **서울 `asia-northeast3`** (Tier 2 가격) |
| 무료 | Free 인스턴스: **15분 무요청이면 잠듦, 깨우는 데 약 1분**, 월 750시간 | Free: 월 $1 크레딧 (0.5GB RAM) — 상시 운영에는 부족 | 월 200만 요청 · 18만 vCPU-초 무료 (요청 기반 과금) |
| 상시 가동 비용 | Starter(0.5 CPU / 512MB) — 가격은 요금표 확인 | Hobby $5/월 (사용량 $5 포함) | 최소 인스턴스 1 을 두면 유휴 시간도 과금 |
| 잠자기 | Free 는 잠듦, 유료는 상시 | 기본 상시 (Serverless 는 선택) | 기본 0 까지 줄어듦 → 콜드 스타트, 최소 인스턴스로 방지 |
| 환경변수 · HTTPS · PORT | ✅ · ✅ · ✅ | ✅ · ✅ · ✅ | ✅(Secret Manager) · ✅ · ✅ |
| health check | HTTP 경로 지정 가능 | HTTP 경로 지정 가능 | 시작 · 활성 프로브 설정 가능 |
| RECARRY 관점 | 가장 단순. 무료는 시연 중 1분 대기가 생긴다. DB 까지 서울↔싱가포르 왕복 | 단순 + 상시 가동이 저렴. 지역은 싱가포르 | DB 와 같은 서울. 대신 설정이 많고, 0 까지 줄면 Java 콜드 스타트 · 로그인 실패 기록 초기화 |

같은 조건(Java 17 · 실행 jar · 환경변수 · HTTPS · PORT · 상시 HTTP 서버 · 외부 DB 연결 · health check · GitHub)은 세 곳 모두 충족한다.
그 밖에 Fly.io(도쿄 `nrt`, 서울 없음, 상시 VM 소액 과금)도 가능하다. Koyeb 무료 인스턴스는 프랑크푸르트 · 워싱턴만이라 제외.

출처: [Render Free](https://render.com/docs/free) · [Render Regions](https://render.com/docs/regions) · [Render Compute](https://render.com/docs/compute-plans) ·
[Railway Plans](https://docs.railway.com/reference/pricing/plans) · [Railway Regions](https://docs.railway.com/reference/deployment-regions) · [Railway Sleeping](https://docs.railway.com/reference/app-sleeping) ·
[Cloud Run Locations](https://docs.cloud.google.com/run/docs/locations) · [Cloud Run Min instances](https://docs.cloud.google.com/run/docs/configuring/min-instances) · [Cloud Run Pricing](https://cloud.google.com/run/pricing) ·
[Fly.io Regions](https://docs.fly.io/reference/regions/)

## 3. Production 환경변수

실제 값은 호스팅의 secret/환경변수 설정에만 넣는다. 로컬 점검용 `backend/.env.prod` 는 git 에 올라가지 않는다.

| 변수 | 필수 | 설명 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | ✅ | `prod` (다른 값 섞지 않는다 — `local` 이면 기동 거부) |
| `DB_URL` | ✅ | `jdbc:postgresql://aws-0-ap-northeast-2.pooler.supabase.com:5432/postgres?sslmode=require` (`sslmode` 없으면 기동 거부) |
| `DB_USERNAME` | ✅ | Supabase session pooler 사용자 (`postgres.<project-ref>`) |
| `DB_PASSWORD` | ✅ secret | Supabase DB 비밀번호 |
| `JWT_SECRET` | ✅ secret | 32바이트 이상 임의 문자열. 로컬과 다른 값. 바꾸면 모든 로그인이 풀린다 |
| `CORS_ALLOWED_ORIGINS` | ✅ | 프론트 origin, 쉼표 구분, 정확한 값만 (`*` 는 기동 거부). 예: `https://<앱>.vercel.app` |
| `COOKIE_SAME_SITE` | | 기본 `Lax` (rewrite 구성). 프론트가 backend 를 직접 부르는 cross-site 구성이면 `None` |
| `DB_POOL_SIZE` | | 기본 5 — Supabase pooler 연결 한도 안에서 |
| `JWT_TTL_MINUTES` | | 기본 120 |
| `ADMIN_EMAIL` · `ADMIN_PASSWORD` | 처음 한 번 | 실제 관리자 생성용. 만든 뒤에는 `ADMIN_PASSWORD` 를 지운다 |
| `PORT` | | 호스팅이 준다 |
| `FORWARD_HEADERS_STRATEGY` | | 기본 `native` (사설 IP 프록시의 X-Forwarded-* 만 신뢰) |
| `LOGIN_MAX_FAILURES_PER_IP` | | 기본 30. Vercel rewrite 뒤에서는 요청이 Vercel 엣지 IP 몇 개로 모이므로 크게(예: 300) 올린다 — 계정별 제한(5회)은 그대로 |

쓰지 않는 것: `COOKIE_SECURE`(prod 는 항상 Secure — false 면 기동 거부), `RECARRY_ENV_FILE`(로컬 전용), `TEST_DB_*`(CI 전용).

## 4. Vercel 설정

1. Project Settings → Environment Variables: `VITE_API_BASE_URL` 은 **설정하지 않는다** (같은 origin `/api`).
2. backend 주소가 정해지면 `vercel.json` 을 아래처럼 바꾼다 — **`/api` 규칙이 SPA 규칙보다 먼저**여야 한다:

```json
{
  "rewrites": [
    { "source": "/api/(.*)", "destination": "https://<backend-host>/api/$1" },
    { "source": "/(.*)", "destination": "/" }
  ],
  "headers": [
    { "source": "/api/(.*)", "headers": [{ "key": "x-vercel-enable-rewrite-caching", "value": "0" }] }
  ]
}
```

- Vercel rewrite 는 환경변수를 쓸 수 없어서 backend 주소를 파일에 적는다 (비밀 값이 아니다).
- 규칙은 위에서부터 적용된다 → `/api` 가 SPA 규칙보다 먼저. 정적 파일(`/assets/*`, `/images/*`)은 rewrite 보다 먼저 서빙되므로 SPA 규칙과 충돌하지 않는다.
- **캐시 주의**: 2026-04-06 이후 만든 Vercel 프로젝트는 외부 rewrite 응답을 upstream 의 cache 헤더대로 캐시한다.
  backend 는 모든 `/api` 응답에 `Cache-Control: no-cache, no-store` 를 보내지만(확인함), 사용자별 응답 · `Set-Cookie` 가
  섞이지 않도록 `x-vercel-enable-rewrite-caching: 0` 으로 한 번 더 끈다. ([Vercel Rewrites](https://vercel.com/docs/rewrites))
- 지금 `vercel.json` 에는 SPA 규칙만 있다. 이대로 배포하면 `/api/*` 가 index.html 로 가고 프론트는
  "서버 응답을 읽을 수 없습니다" 오류를 보여준다 (카탈로그 · 로그인 동작 안 함).

## 5. CORS / Cookie / Auth

- CORS: `allowCredentials=true` + **정확한 origin 목록만**. 목록 밖 origin 의 요청은 403 (확인함). 로컬은 Vite proxy 가 Host 를 유지해 CORS 목록이 필요 없다.
- 세션 쿠키 `recarry_session`: `HttpOnly; Secure; SameSite=Lax; Path=/api`, 만료 = `JWT_TTL_MINUTES`.
  응답 본문 · localStorage 에 토큰 없음. 상태 변경 요청은 `X-Recarry-Client` 헤더 필수(CSRF).
- HTTPS: 브라우저 ↔ Vercel ↔ backend 호스팅 모두 https 여야 Secure 쿠키가 오간다 (backend URL 은 https 로).

## 6. 관리자 · 테스트 계정 정리

현재 운영 DB 에는 E2E 에 쓴 **테스트 계정만** 있다 — 모두 `@test.recarry` (관리자 `e2e-admin@test.recarry` 포함), 예약은 전부 `CANCELLED`.
테스트 관리자는 운영 계정으로 쓰지 않는다.

1. 실제 관리자: 호스팅 환경변수(또는 `.env.prod`)의 `ADMIN_EMAIL` · `ADMIN_PASSWORD` 를 **실제 값으로 바꾸고** 한 번 기동 → 생성됨 → `ADMIN_PASSWORD` 삭제.
   (`AdminBootstrap` 은 이미 있는 계정의 비밀번호를 바꾸지 않는다. 권한만 올린다.)
2. 테스트 관리자 권한 회수 (운영자가 확인 후 수동, Supabase SQL Editor):
   ```sql
   UPDATE users SET role = 'USER' WHERE email = 'e2e-admin@test.recarry';
   ```
3. 테스트 계정 · 예약 삭제가 필요하면 (되돌릴 수 없음 — 백업 후, 트랜잭션으로, 건수를 확인하며 **수동으로만**):
   ```sql
   BEGIN;
   SELECT count(*) FROM users WHERE email LIKE '%@test.recarry';          -- 예상 건수 확인
   DELETE FROM bookings WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@test.recarry');
   DELETE FROM users WHERE email LIKE '%@test.recarry';
   -- 건수가 맞으면 COMMIT; 아니면 ROLLBACK;
   ```

## 7. Demo 데이터 운영

운영 DB 의 카탈로그는 **DEMO**다 (`backend/db/demo/demo_catalog.sql`, 모든 캐리어 ID 가 `RC-<사이즈>-S<번호>`).

- 구분: `carrier_code ~ '^RC-[0-9]{2}-S[0-9]{2}$'` 이면 demo. 실제 캐리어는 S 없는 ID 를 쓴다.
- 실제 데이터로 넘어갈 때 (안전한 순서):
  1. 실제 캐리어 등록 → 2. `carrier_models` 가격 · 문구는 확정값으로 **수정** (행 삭제 금지 — 실제 캐리어가 참조)
  → 3. demo 캐리어는 관리자 화면에서 **`UNAVAILABLE`** (예약 · 대여 가능 대수에서 빠짐, 기록 보존)
  → 4. 완전 삭제는 예약 참조가 없음을 확인한 뒤 백업 후 수동으로만. 자동화하지 않는다.
- 최소 등록 기능 설계 (아직 구현하지 않음 — `BACKEND.md` §15):
  `POST /api/admin/carriers` (size · carrierCode · grade · collectedFrom · repairSummary · inspectedAt, 상태 `INSPECTION` 으로 시작),
  `POST /api/admin/carriers/{id}/events`, `PATCH /api/admin/carrier-models/{size}` (가격·문구).
  코드 규칙 검증: S 로 끝나는 번호는 demo 전용으로 막아 실제 등록과 섞이지 않게 한다.

## 8. CI

`.github/workflows/ci.yml` — push(master/main) · PR 마다:
- frontend: `npm ci` → `npm run build`
- backend: postgres:15 서비스 컨테이너(`recarry_test`) → `./gradlew clean build` (Flyway V1·V2 + sample → 통합 테스트 25개 → bootJar)
- 비밀 값 없음 (DB 는 잡이 끝나면 사라지는 컨테이너, JWT 키는 테스트가 생성). 실패 시 테스트 리포트를 artifact 로 올린다.

## 9. 배포 후 E2E 체크리스트 (`https://<앱>.vercel.app`)

**Anonymous**
- [ ] Home — Hero 에 대표 캐리어 ID(`RC-24-S01`), Collection 카드 3개(가격 19,900 / 25,900 / 31,900원), Story 타임라인
- [ ] Product 20 · 24 · 28 — 가격 · 검수 5/5 · 타임라인 · 갤러리 · 날짜 선택
- [ ] 브라우저 콘솔 에러 0, `GET /api/auth/session` 200 (`user: null`)
- [ ] `https://<backend-host>/api/health` 200

**회원**
- [ ] 회원가입 → 쿠키 `recarry_session` 이 HttpOnly · Secure · SameSite=Lax (DevTools → Application → Cookies)
- [ ] Product 에서 날짜 선택 → 예약하기 → (로그인 상태) Step 02 부터 → 배송 정보 → 확인 → 완료 화면에 예약번호
- [ ] My Page 에 예약 표시 → 상세 → 예약 요청 취소 → 상태 "취소"
- [ ] 로그아웃 → My Page 접근 시 로그인 화면

**관리자** (실제 관리자 계정)
- [ ] `/admin` — 재고 · 캐리어 6대 · 예약 목록
- [ ] 예약 상태 변경(예약 요청 → 예약 확정) · 캐리어 운영 상태 변경 후 원복

**보안**
- [ ] USER 로 `/admin` → 403 화면, `GET /api/admin/carriers` → 403
- [ ] 다른 계정의 예약번호로 `/api/bookings/{no}` → 404
- [ ] 로그아웃 후 `GET /api/bookings` → 401, 쿠키 값을 바꿔 보내면 401
- [ ] 같은 기간 28" 두 번 예약 → 두 번째 409
- [ ] `expectedTotal` 을 바꿔 `POST /api/bookings` → 409 `PRICE_MISMATCH`
- [ ] 오늘 · 과거 출발일 → 400 `INVALID_DATES`
- [ ] 목록에 없는 origin 에서 POST → 403
- [ ] Supabase Dashboard → Security Advisor: RLS 경고 없음 (btree_gist 가 public 에 있다는 경고는 알려진 항목)

체크 후 테스트 예약은 취소해 둔다 (demo 재고가 막히지 않게).

## 10. 배포 순서

| # | 단계 | 확인 |
|---|---|---|
| 1 | backend 호스트 생성 (§2a 에서 결정 — 지역은 가능하면 서울 가까이) | 인스턴스 1대, 512MB 이상 |
| 2 | GitHub 저장소 연결 (루트 디렉터리 `backend/`, Dockerfile 또는 `./gradlew bootJar` + `java -jar build/libs/recarry-backend.jar`) | 먼저 commit · push 가 필요하다 — Vercel 자동 배포 전에 §4 를 마칠 것 |
| 3 | 환경변수 설정 (§3) — `ADMIN_*` 는 **실제 관리자 값** | `CORS_ALLOWED_ORIGINS` 에 Vercel 도메인 |
| 4 | Deploy | 빌드 로그에 테스트 생략(`-x test`), 기동 로그에 `The following 1 profile is active: "prod"` |
| 5 | `https://<backend>/api/health` → 200 `{"status":"UP"}` | 호스팅 health check 경로도 `/api/health` 로 |
| 6 | Supabase migration 상태 | 기동 로그 `Schema "public" is up to date` (V2), guard 거부 · CORS 경고 없음 |
| 7 | 관리자 계정 생성 확인 | 로그 `created ADMIN account: <실제 이메일>` → 호스팅에서 `ADMIN_PASSWORD` 삭제 → 재배포 |
| 8 | backend 공개 URL 확인 | https 인지 |
| 9 | `vercel.json` 에 `/api` rewrite + 캐시 끄기 (§4) | `/api` 규칙이 SPA 규칙보다 위 |
| 10 | Vercel deploy | `https://<앱>/api/health` 가 rewrite 로 200 |
| 11 | 회원가입 | 쿠키 HttpOnly · Secure · SameSite=Lax |
| 12 | 로그인 | `GET /api/auth/session` 에 사용자 |
| 13 | 예약 (날짜 → 캐리어 → 배송 → 확인 → 완료) | 예약번호, 서버 금액 |
| 14 | My Page — 목록 · 상세 · 취소 | 상태 "취소" |
| 15 | Admin — 캐리어 · 예약 · 상태 변경 | 실제 관리자 계정으로 |
| 16 | 로그아웃 | 보호 화면 → 로그인 |
| 17 | 보안 테스트 (§9 보안 항목) | 끝나면 테스트 예약은 취소 |

그 다음: 테스트 관리자 권한 회수(§6-2), 실제 캐리어 데이터 준비 시 §7.

## 11. Render (선택된 호스팅)

설정 파일: 저장소 루트 [`render.yaml`](../render.yaml) (Blueprint). 비밀 값은 들어 있지 않다.

| 항목 | 값 |
|---|---|
| 서비스 | `recarry-backend`, Docker, **Singapore**, 인스턴스 1 |
| 플랜 | **`free`** (0.1 CPU / 512MB, 월 750시간 — 1대 24시간 운영 가능). 15분 무요청이면 잠들고, 다음 요청 때 JVM 기동까지 1분 이상 걸린다.
잠든 동안 로그인 실패 기록은 초기화된다. 시연 전에는 `/api/health` 를 한 번 열어 깨워 둔다. 상시 가동이 필요하면 `starter`(유료)로 바꾼다 |
| 빌드 | `./backend/Dockerfile`, context `./backend` (경로는 저장소 루트 기준) — `backend/.dockerignore` 가 `.env*` 를 막는다 |
| 재배포 | `backend/` 변경 commit 마다 (`rootDir: backend` — 프론트 변경으로는 재배포하지 않는다) |
| 포트 | Render 가 `PORT=10000` 을 준다 → `server.port: ${PORT}` 로 바인딩 (0.0.0.0) |
| TLS | Render 로드밸런서가 종료 → 앱은 http 로 받는다 (prod 는 사설 IP 프록시의 X-Forwarded-* 를 신뢰) |
| Health check | `/api/health` — 5초 안에 2xx. 새 배포는 health 통과 후에만 트래픽을 받는다 (무중단) |
| 주소 | `https://recarry-backend.onrender.com` 형식 (이름이 이미 쓰였으면 접미사가 붙는다 — 대시보드에서 확인) |

### 대시보드에 입력할 값 (`sync: false`)

| 키 | 넣을 값 |
|---|---|
| `DB_USERNAME` | Supabase session pooler 사용자 (`postgres.<project-ref>`) |
| `DB_PASSWORD` | Supabase DB 비밀번호 |
| `CORS_ALLOWED_ORIGINS` | 실제 Vercel origin (`https://<앱>.vercel.app`) — 모르면 먼저 Vercel 프로젝트 도메인을 확인 |
| `ADMIN_EMAIL` · `ADMIN_PASSWORD` | 실제 관리자 (테스트 계정 아님) |

자동: `JWT_SECRET` (Render 생성), `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `COOKIE_SAME_SITE=Lax`, `DB_POOL_SIZE=5`, `JWT_TTL_MINUTES=120`, `LOGIN_MAX_FAILURES_PER_IP=300`.

### 순서

1. commit · push (이때 `vercel.json` 은 아직 바꾸지 않는다 — Vercel 자동 배포가 나가도 프론트는 "서버 응답을 읽을 수 없습니다"만 보여준다.
   시연 중인 Vercel 사이트가 있다면 push 전에 §4 를 함께 준비하거나 Vercel 자동 배포를 잠시 끈다)
2. Render → New → **Blueprint** → 저장소 선택 → 위 표의 값 입력 → Apply (free 는 결제 수단 없이 가능)
3. 배포 로그 확인: `The following 1 profile is active: "prod"`, `Schema "public" is up to date`, `created ADMIN account: <실제 이메일>`,
   `CORS_ALLOWED_ORIGINS is empty` 경고가 **없어야** 한다
4. `https://<render 주소>/api/health` → `{"status":"UP"}`
5. Render → Environment 에서 `ADMIN_PASSWORD` 삭제 → 저장(재배포)
6. `vercel.json` 에 §4 규칙(`https://<render 주소>/api/$1`) → commit · push → Vercel 배포
7. §9 체크리스트 → 테스트 관리자 권한 회수(§6-2)
