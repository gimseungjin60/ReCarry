-- V5: 운영자가 "예약 요청(결제 전)"을 취소한 경우를 REJECTED 로 구분한다.
-- ADMIN 은 확정 이후 운영자 취소만 뜻한다. 탈퇴 시 결제 전 단계에서 끝난 예약(USER · EXPIRED · REJECTED)의
-- 배송 정보를 지우고, 확정 이후 기록(ADMIN · RETURNED)은 보관 의무 때문에 남긴다 (docs/POLICY.md).
ALTER TABLE bookings DROP CONSTRAINT bookings_cancel_reason_check;
ALTER TABLE bookings ADD CONSTRAINT bookings_cancel_reason_check
    CHECK (cancel_reason IN ('USER', 'ADMIN', 'EXPIRED', 'REJECTED'));
