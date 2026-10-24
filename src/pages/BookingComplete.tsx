import { Navigate, useLocation } from 'react-router-dom'
import Button from '@/components/Button'
import BankTransfer from '@/components/BankTransfer'
import BookingTicket from '@/components/BookingTicket'
import type { Booking } from '@/data/types'
import { useTitle } from '@/lib/useTitle'

/* PRD §4 Complete — 예약번호 / 캐리어 / 이용 날짜 / 배송 정보 / 총 금액 + 무통장 입금 안내.
   Booking 이 POST /api/bookings 응답(서버에 저장된 예약)을 state 로 넘긴다. */

export default function BookingComplete() {
  useTitle('예약 요청 완료')
  const b = useLocation().state as Booking | null

  // 예약을 거치지 않고 직접 들어온 경우
  if (!b?.bookingNumber) {
    return <Navigate to="/booking" replace />
  }

  return (
    <div className="wrap done-pg">
      <p className="label muted">Reservation requested</p>
      <h1 className="d-l" style={{ marginTop: 24 }}>
        예약 요청이
        <br />
        접수되었습니다.
        <br />
        <span className="muted">{b.carrierCode}의 다음 여행을 준비합니다.</span>
      </h1>

      <BookingTicket booking={b} />

      {b.payment && <BankTransfer pay={b.payment} />}
      <p className="small" style={{ marginTop: 20, maxWidth: 560 }}>
        여행을 마친 캐리어는 세척과 검수를 거쳐 다음 여행자에게 전달됩니다.
      </p>

      <div style={{ marginTop: 40, display: 'flex', gap: 16, flexWrap: 'wrap' }}>
        <Button to={`/mypage/bookings/${b.bookingNumber}`} arrow>
          예약 내역 보기
        </Button>
        <Button to="/" variant="secondary">
          홈으로
        </Button>
      </div>
    </div>
  )
}
