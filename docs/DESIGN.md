# RECARRY Design System

## 1. Design Philosophy

RECARRY는 Premium Travel Brand처럼 보인다.

키워드:

- Premium
- Travel
- Circular
- Modern
- Editorial

---

## 2. Visual Principles

### Whitespace

콘텐츠를 화면에 과도하게 채우지 않는다.

### Typography

크기와 간격을 통해 위계를 만든다.

### Photography

강한 여행 이미지를 선택적으로 사용한다.

### Color

화이트 / 샌드 / 포레스트 / 다크를 중심으로 사용한다.

환경 관련 메시지를 표현할 때도
초록색을 남용하지 않는다.

---

## 3. Color

Primary:
#141413

Forest:
#1e3b2f

Sand:
#e3d8c5

Canvas:
#f6f4ef

Stone:
#e9e5dd

Night:
#121211

Signal:
#c4592b

---

## 4. Typography

Display:
Inter Tight

Body:
Pretendard

Mono:
IBM Plex Mono

Display는 과도하게 Bold하지 않는다.

---

## 5. Layout

Desktop max width:
1440px

Grid:
12 columns

Desktop gutter:
24px

Mobile horizontal padding:
20px

Large sections:
96px 이상 사용

---

## 6. Radius

기본:

4px ~ 12px

Primary brand surface:

필요한 경우 낮은 radius 사용

Pill UI는 필요한 곳에서만 사용한다.

---

## 7. Main Components

- Navbar
- Button
- Product Card
- Product Detail
- Calendar
- Booking Stepper
- Carrier Passport
- Process Ring
- Story Timeline
- CTA

---

## 8. Hero

현재 V2 방향:

- Full-bleed travel photography
- Dark overlay
- Large typography
- Strong CTA
- Travel atmosphere

Hero immersive animation은
components/scroll-expansion-hero.md를 참고한다.

---

## 9. Motion

Motion은 브랜드를 보조해야 한다.

사용 가능:

- fade
- slide
- scale
- parallax
- scroll progress
- image reveal

사용 금지:

- excessive bounce
- excessive glow
- constant movement
- unnecessary 3D effects
---

## Apple 원칙 적용 (2026-10)

참고: `docs/DESIGN-apple.md` (getdesign.md 의 Apple 웹 분석). **원칙만 옮기고 브랜드는 복제하지 않는다** (CLAUDE.md §8).
값은 `src/styles/tokens.css` 한 곳에 있다.

| Apple 원칙 | RECARRY 적용 |
|---|---|
| 면의 교차로 구획 (white ↔ parchment ↔ dark), 테두리 최소 | `--canvas` = parchment `#f5f5f7`, 다크 블록은 night `#1d1d1f` · 브랜드 블록은 forest 유지 |
| 강조색 하나 (Action Blue) | 브랜드 색 **Boarding Blue** 하나 (아래 §Brand color). Apple 블루와 다른 RECARRY 고유 값 |
| SF Pro, 제목 600 + 촘촘한 자간, 본문 17px | Pretendard (한글) · 제목 600 · 자간 -0.025~-0.035em · 본문 17px/1.6 · 500 굵기는 제목에 쓰지 않음 |
| pill CTA, 누르면 scale(.95) | `.btn` pill 48px, `:active` scale(.96) |
| 반경 문법: 도구 8 · 카드 18 · 행동 pill | `--radius-sm 8` · `--radius-md 12`(입력) · `--radius-lg 18`(카드·제품 프레임) · pill |
| 그림자는 제품 이미지에만 | `--shadow-product` 토큰만 둔다. UI 에는 그림자 없음 |
| 반투명 blur 내비, parchment 푸터 | nav `rgba(255,255,255,.78)` + `blur(20px)` 56px · footer parchment, 촘촘한 링크 열 |
| 화면 가득 채우는 이미지는 직각 | 모바일 제품 갤러리·Before/After 는 직각, 카드 안 이미지는 18 |

유지하는 RECARRY 요소: Carrier ID(mono) · 검수 표시 · Carrier Story · Circular Journey(forest 블록) · 캐리어 일러스트의 signal 오렌지 태그.

## Brand color — Boarding Blue (2026-10 확정)

탑승권의 파랑 + 검수 도장의 주황. 흔한 화이트/베이지 · Apple 모노 · 친환경 초록을 피하고, 여행(탑승권)과 Carrier ID(태그·도장)를 색으로 말한다.

| 토큰 | 값 | 쓰는 곳 |
|---|---|---|
| `--brand` | `#2340E0` | 히어로 면 · 주요 버튼 · 마지막 CTA 띠 · 포커스 링 |
| `--brand-ink` | `#1A30B8` | 누름 상태 · 밝은 면 위 링크 |
| `--night` | `#0E1530` | 본문 글자(ink) · Carrier ID 다크 섹션 |
| `--canvas` | `#F6F7FB` | ticket white — 교차 섹션 · 푸터 |
| `--stamp` | `#FF6A3D` | 캐리어 태그 · 검수 표시 · 오류 — **이 용도에만** |
| `--mist` | `#C9D3F5` | 브랜드 면 위 보조 글자 · 제품 프레임 톤 |

## Home 구조 (2026-10 개편 — 5개 블록)

렌탈 서비스 벤치마크(쏘카 · Hygglo · Grover · bag4days 등) 결과: 첫 화면에서 **무엇을 · 얼마에 · 어떻게 받는지**를 답하고 바로 예약으로 들어간다.

1. Hero (`#top`) — 헤드라인 + **예약 바**(사이즈 · 날짜 → /booking) + 칩(최저가 · 출발 전날 도착 · 반납일 회수) + 대표 캐리어
2. Sizes (`#collection`) — 3개 카드: 2박 3일 가격 · 1박 추가 · 실제 검수 결과
3. How it works (`#how`) — 고객 4단계 + 순환 공정 한 줄
4. Carrier ID (`#story`) — Before/After · Passport · 실제 Story 타임라인 (예전 Why · 순환 링 · Story 3개를 하나로)
5. FAQ (`#faq`) + 마지막 CTA — 대여 정책 요약, /policy 연결

원칙: 지어낸 후기 · 이용자 수 · 언론 로고는 쓰지 않는다. 신뢰는 검수 기록 공개와 정책(보증금 · 만료 · 환불)으로 만든다.
