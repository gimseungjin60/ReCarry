# RECARRY Web MVP — PRD

## 1. Product

RECARRY

버려지는 캐리어를 다시 여행으로.

RECARRY는 폐기 예정 캐리어를 회수한 뒤
세척·수리·검수하여 다시 렌탈하는
자원순환형 캐리어 렌탈 서비스다.

---

## 2. MVP Goal

이번 웹 MVP의 목적:

- RECARRY 서비스 이해
- 브랜드 경험 전달
- 캐리어 상품 탐색
- 캐리어 상세 확인
- 예약 흐름 체험

실제 결제나 실제 배송 운영을 구현하는 것이 목적이 아니다.

---

## 3. User Journey

Home
→ Collection
→ Product
→ Booking (로그인 없이 4단계 → 마지막 "예약 요청"에서 로그인 · 카카오 간편가입)
→ Complete (무통장 입금 안내)
→ My Page (입금 확인 · 확정 · 취소/환불 · 보증금 정산)

쇼핑몰 주문서처럼 결제(예약 요청) 직전에만 로그인한다. 고른 내용은 같은 탭에 보관되어
로그인 · 가입 · 카카오를 다녀와도 이어진다. 장바구니는 두지 않는다 (한 여행 = 한 예약) —
2026-10-25 벤치마크 문서 참고.

---

## 4. Pages

### Home

목적:
RECARRY 브랜드와 서비스 가치를 전달한다.

Sections:

1. Hero
2. Why RECARRY
3. Circular Process
4. Collection
5. Carrier Story
6. Why Rent
7. CTA

---

### Collection

캐리어 상품을 탐색한다.

표시 정보:

- Size
- Grade
- Price
- Capacity
- Usage
- Inspection status

현재 기본 상품:

- 20"
- 24"
- 28"

---

### Product Detail

상품 상세 정보:

- Product image
- Product name
- Size
- Grade
- Price
- Availability
- Inspection
- Cleaning
- Repair
- Product story
- Travel history
- Booking CTA

---

### Booking

예약 단계:

1. 여행 날짜
2. 캐리어 선택
3. 배송 정보
4. 예약 확인

- 로그인 없이 진행, 4단계 "예약 요청"에서 비회원이면 로그인 선택(카카오 · 이메일 · 회원가입)을 보여준다.
- 배송 정보: 회원 정보로 받는 분 · 연락처, 최근 예약의 배송지, 카카오 우편번호 검색.
- 서버가 가격 · 재고 · 날짜를 다시 검증해 저장한다 (backend).

---

### Complete

예약 결과:

- 예약번호
- 캐리어
- 이용 날짜
- 배송 정보
- 총 금액 (대여 요금 + 보증금)
- 무통장 입금 안내 (계좌 · 금액 · 입금자명 · 기한)

---

## 5. RECARRY Differentiators

일반 캐리어 렌탈과 차별되는 핵심 요소:

### Carrier Story

하나의 캐리어가

회수
→ 수리
→ 세척
→ 검수
→ 새로운 여행

을 거치는 과정을 보여준다.

### Carrier ID

각 캐리어를 개별 자산처럼 보여준다.

### Inspection

고객에게 검수 완료 상태를 보여준다.

### Circular Journey

캐리어의 한 번의 여행으로 끝나지 않는
순환 구조를 강조한다.

---

## 6. Out of Scope

현재 제외:

- 실결제 PG (지금은 무통장 입금 — 관리자가 입금 확인)
- 실제 배송 연동
- 장바구니 · 비회원 주문
- 쿠폰
- 포인트
- 리뷰
- 구독
- B2B
- AI 추천
- 실제 관리자 시스템
- 실시간 재고 API