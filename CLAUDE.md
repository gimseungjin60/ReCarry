# RECARRY — Claude Development Rules

## 1. Project

RECARRY는 버려지는 캐리어를 회수하여
세척·수리·검수 후 다시 필요한 고객에게 렌탈하는
자원순환형 캐리어 렌탈 서비스다.

핵심 메시지:

> 버려지는 캐리어를 다시 여행으로.

현재 프로젝트의 목표는 실제 사업을 준비하면서 제작하는
RECARRY Web MVP다.

완성형 상용 서비스가 아니라
브랜드와 서비스의 가치 전달,
상품 탐색,
예약 경험을 검증할 수 있는 웹을 만든다.

---

## 2. Current Goal

현재 개발 목표:

1. RECARRY 브랜드 웹 구현
2. 실제 서비스처럼 보이는 상품 탐색 경험
3. 캐리어 상세 확인
4. 여행 날짜 선택
5. 간단한 예약 흐름
6. RECARRY의 자원순환 스토리 전달

현재 단계에서는 다음 기능을 구현하지 않는다.

- 실결제 PG
- 실제 배송 API
- 회원가입/로그인
- 쿠폰
- 포인트
- 리뷰 시스템
- 구독 결제
- B2B 관리 시스템
- 복잡한 관리자 시스템
- AI 추천
- 실제 운영 자동화

필요할 경우 추후 별도 단계에서 추가한다.

---

## 3. Tech Stack

Frontend:

- React
- Vite
- TypeScript
- Tailwind CSS

UI:

- shadcn/ui where appropriate
- Lucide icons
- Framer Motion for animation

Important:

현재 프로젝트는 Next.js 프로젝트가 아니다.

따라서 Next.js 전용 API를 무조건 사용하지 않는다.

예:

- next/image 사용 금지
- next/link 사용 금지
- Next.js App Router 전제 금지

필요한 컴포넌트는 React + Vite 환경에 맞게 변환한다.

---

## 4. Development Principle

### 가장 중요한 원칙

"기능을 많이 만드는 것"보다
"RECARRY가 실제 브랜드처럼 보이는 것"을 우선한다.

기존 디자인과 구조를 존중한다.

새로운 기능이나 페이지를 임의로 추가하지 않는다.

새로운 기능이 필요하다고 판단되면
먼저 기존 PRD와 디자인을 확인한다.

---

## 5. Business Accuracy

실제 사업계획서에 없는 내용을
확정된 사실처럼 표현하지 않는다.

다음 정보는 특히 주의한다.

- 가격
- 배송비
- 파손 정책
- 환불 정책
- 보증금
- 재고
- 회수처
- 협력기관
- 실제 운영 지역

Prototype에서 사용하는 데이터는
필요한 경우 sample/prototype 데이터임을 명확하게 한다.

---

## 6. Product Concept

RECARRY는 일반적인 캐리어 쇼핑몰처럼 보이면 안 된다.

핵심은:

폐기 예정 캐리어
→ 회수
→ 세척
→ 수리
→ 검수
→ 렌탈
→ 반납
→ 다시 여행

이다.

특히 다음 요소를 브랜드 차별점으로 유지한다.

- Carrier ID
- Inspection
- Cleaning
- Repair
- Grade
- Carrier Story
- Circular Journey

---

## 7. Current Main Flow

Home
→ Collection
→ Product Detail
→ Booking
→ Booking Complete

예약 단계:

1. 여행 날짜
2. 캐리어 선택
3. 배송 정보
4. 예약 확인

현재 단계에서는 이 흐름을 유지한다.

---

## 8. Design Direction

RECARRY는 다음 분위기를 가진다.

- Premium
- Sustainable
- Travel
- Modern

일반적인 친환경 사이트처럼
초록색을 반복 사용하지 않는다.

전체적인 디자인은:

- 넓은 whitespace
- editorial layout
- restrained typography
- premium travel photography
- dark / forest / sand tones
- minimal shadow
- minimal decoration

을 기본으로 한다.

참고 디자인의 원칙은 적용하되
특정 브랜드의 디자인을 그대로 복제하지 않는다.

---

## 9. Image Usage

현재 이미지들은 디자인 검증용 assets다.

Pinterest 및 외부 이미지가 포함되어 있을 수 있으므로
실제 공개 전에 라이선스를 확인한다.

이미지보다 브랜드 메시지와 레이아웃이 우선이다.

---

## 10. Animation

애니메이션은 디자인 구조를 해치지 않는 범위에서 사용한다.

현재 우선순위:

1. Hero immersive animation
2. Scroll-based movement
3. Process animation
4. Before/After interaction
5. subtle hover interaction

과도한 animation,
parallax 남발,
scroll hijacking은 피한다.

Hero immersive component 관련 상세 구현은:

docs/components/scroll-expansion-hero.md

를 참고한다.

단, 해당 문서가 Next.js 전용 코드를 포함하고 있다면
현재 React + Vite 구조에 맞게 변환해서 사용한다.

---

## 11. Component Architecture

재사용 가능한 컴포넌트를 우선한다.

예:

- Navbar
- Button
- SectionHeader
- ProductCard
- ProductGallery
- Calendar
- BookingSummary
- CarrierStatus
- CarrierStory
- ProcessTimeline
- CTASection

페이지에 동일한 UI를 반복해서 직접 작성하지 않는다.

---

## 12. Coding Rules

- TypeScript 사용
- any 남용 금지
- 컴포넌트는 작게 유지
- 데이터와 UI를 분리
- 하드코딩된 문자열 반복 최소화
- CSS는 기존 디자인 시스템을 우선 사용
- 불필요한 dependency 추가 금지
- 기존 코드 재사용 우선
- 기존 기능을 깨뜨리는 대규모 refactoring 금지

---

## 13. Before Coding

새로운 기능을 구현하기 전에:

1. 현재 코드 확인
2. PRD 확인
3. DESIGN 확인
4. 기존 component 확인
5. 필요한 경우 새 component만 추가

"새로 만드는 것"보다
"기존 것을 재사용하는 것"을 우선한다.

---

## 14. Definition of Done

기능이 구현되었다는 것은 단순히 코드가 작성됐다는 의미가 아니다.

다음 조건을 확인한다.

- Desktop 정상 작동
- Mobile 정상 작동
- 기존 UI와 스타일 일관성 유지
- console error 없음
- broken route 없음
- 핵심 사용자 흐름 정상 작동
- 기존 기능 regressions 없음