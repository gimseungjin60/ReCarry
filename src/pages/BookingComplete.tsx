import { Navigate, useLocation } from 'react-router-dom'
import Button from '@/components/Button'
import { CARRIERS, isCarrierSize } from '@/data/carriers'
import { won } from '@/lib/format'

/* PRD §4 Complete — 예약번호 / 캐리어 / 이용 날짜 / 배송 정보 / 총 금액.
   날짜와 배송 주소는 Booking 이 실제로 수집하는 Step 3 이후에 채워진다. */

interface BookingResult {
  size?: string
  total?: number
  code?: string
}

export default function BookingComplete() {
  const { state } = useLocation()
  const result = state as BookingResult | null

  // 예약을 거치지 않고 직접 들어온 경우
  if (!result?.code || !isCarrierSize(result.size)) {
    return <Navigate to="/booking" replace />
  }

  const p = CARRIERS[result.size]

  return (
    <div className="wrap done-pg">
      <p className="label muted">Reservation confirmed</p>
      <h1 className="d-l" style={{ marginTop: 24 }}>
        예약이 완료되었습니다.
        <br />
        <span className="muted">
          {p.id}의 다음 여행이
          <br />곧 시작됩니다.
        </span>
      </h1>

      <div className="ticket">
        <div className="top">
          <span className="mono">BOOKING {result.code}</span>
          <span className="label">{p.name}</span>
        </div>
        <dl>
          <div>
            <dt>캐리어</dt>
            <dd>
              {p.name} · {p.grade} GRADE
            </dd>
          </div>
          <div>
            <dt>캐리어 ID</dt>
            <dd className="mono">{p.id}</dd>
          </div>
          <div>
            <dt>합계</dt>
            <dd>{won(result.total ?? p.price)}</dd>
          </div>
        </dl>
      </div>

      <p className="small" style={{ marginTop: 20, maxWidth: 560 }}>
        결제 안내와 배송 알림은 입력하신 번호로 카카오톡을 통해 보내드립니다. 여행을 마친 캐리어는 세척과 검수를 거쳐
        다음 여행자에게 전달됩니다.
      </p>

      <div style={{ marginTop: 40, display: 'flex', gap: 16, flexWrap: 'wrap' }}>
        <Button to="/" arrow>
          홈으로
        </Button>
        <Button to={`/product/${p.key}`} variant="secondary">
          이 캐리어의 이야기
        </Button>
      </div>
    </div>
  )
}
