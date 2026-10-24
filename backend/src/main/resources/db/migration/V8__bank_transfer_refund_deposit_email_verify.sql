-- V8: 무통장 입금 · 환불 · 보증금 정산 · 가입 이메일 인증. 결정 기록: docs/POLICY.md
-- 예약 요청(REQUESTED) = 입금 대기. 관리자가 입금을 확인하면 확정(CONFIRMED) — "결제 성공 → 재고 점유" 방향.

ALTER TABLE bookings
    -- 입금 (관리자가 통장 내역을 보고 확인한다)
    ADD COLUMN paid_at             TIMESTAMPTZ,
    ADD COLUMN paid_amount         INTEGER CHECK (paid_amount >= 0),
    ADD COLUMN payer_name          VARCHAR(50),
    -- 결제 후 취소의 환불 (계산은 서버 — 대여 정책 단계별 기준)
    ADD COLUMN refund_amount       INTEGER CHECK (refund_amount >= 0),
    ADD COLUMN refunded_at         TIMESTAMPTZ,
    -- 반납 후 보증금 정산 (파손 · 연체 공제)
    ADD COLUMN deposit_deduction   INTEGER CHECK (deposit_deduction >= 0),
    ADD COLUMN deposit_note        VARCHAR(200),
    ADD COLUMN deposit_settled_at  TIMESTAMPTZ,
    ADD CONSTRAINT bookings_paid_complete CHECK ((paid_at IS NULL) = (paid_amount IS NULL)),
    ADD CONSTRAINT bookings_refund_le_paid CHECK (refund_amount IS NULL OR refund_amount <= COALESCE(paid_amount, 0)),
    ADD CONSTRAINT bookings_deduction_le_deposit CHECK (deposit_deduction IS NULL OR deposit_deduction <= deposit);

-- 가입 이메일 소유 확인. 이 migration 전에 가입한 회원은 NULL (다음 예약 때 인증을 요청한다)
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;
