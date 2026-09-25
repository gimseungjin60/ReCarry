# RECARRY

버려지는 캐리어를 다시 여행으로 — 회수 · 세척 · 수리 · 검수를 거친 캐리어를 렌탈하는 자원순환형 서비스의 Web MVP.

```
frontend (repo 루트)   React + Vite + TypeScript          → Vercel
backend/               Spring Boot 4 · Java 17 · Flyway   → 호스팅 미정 (Java 17 + 환경변수)
database               PostgreSQL                         → 로컬 클러스터 / Supabase (운영)
```

- 설계 · API · 인증 · 스키마: [`docs/BACKEND.md`](docs/BACKEND.md)
- Backend 실행 · 운영 연결: [`backend/README.md`](backend/README.md)
- 배포 절차 · 환경변수 · 배포 후 체크리스트: [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md)
- 제품 · 디자인: [`docs/PRD.md`](docs/PRD.md) · [`docs/DESIGN.md`](docs/DESIGN.md) · 개발 규칙 [`CLAUDE.md`](CLAUDE.md)

결제 · 배송 · 외부 알림은 아직 없다. 가격 · 재고 · 회수처 등은 **sample** 값이다.

**현재 상태**: 운영 DB(Supabase)에 스키마(V1 · V2) 적용, 시연용 **demo** 카탈로그 입력, E2E 확인 완료.
backend 호스팅은 **Render**(`render.yaml`)로 정했다 — 아직 배포 전. 절차는 `docs/DEPLOYMENT.md` §11. CI: `.github/workflows/ci.yml`.

## 로컬 개발

```bash
npm install
cp backend/.env.example backend/.env      # 값 채우기 — backend/README.md §2 (DB 준비 포함)
cd backend && ./gradlew bootRun           # http://localhost:8080 (local 프로필: sample 카탈로그 포함)
npm run dev                               # repo 루트, 다른 터미널 — Vite 가 /api 를 :8080 으로 넘긴다
```

## 운영 구성

| 위치 | 설정 |
|---|---|
| Frontend | `VITE_API_BASE_URL` — 비우면 같은 origin `/api` (권장: Vercel rewrite 로 backend 에 연결. 이때도 backend `CORS_ALLOWED_ORIGINS` 에 Vercel 도메인 필요) |
| Backend | `SPRING_PROFILES_ACTIVE=prod` · `DB_*` (Supabase, `sslmode=require`) · `JWT_SECRET` · `CORS_ALLOWED_ORIGINS` · `COOKIE_SAME_SITE` |
| DB migration | backend 기동 시 Flyway 가 `db/migration` 만 적용 (prod 에서 sample 은 막힌다) |
| Demo 데이터 | `backend/db/demo/demo_catalog.sql` — 운영자가 직접 한 번 실행 (DEMO, 자동 실행 없음) |

비밀 값은 `.env` · `.env.prod` (gitignore) 또는 호스팅 환경변수에만 둔다. 예시: `backend/.env.example`, `backend/.env.prod.example`, `.env.example`.

## 빌드 · 테스트

```bash
npm run build                    # 프론트 (tsc 포함)
cd backend && ./gradlew clean build   # 백엔드 컴파일 + 통합 테스트 (TEST_DB_* 필요)
```
