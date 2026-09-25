import { BOOKING_STATUS } from '@/data/status'
import type { Booking } from '@/data/types'
import { fmt, parseYmd, won } from '@/lib/format'

/* 예약 티켓 (prototype 의 done 화면 .ticket). Complete · My Page 상세 · Admin 상세가 같이 쓴다.
   full 이면 배송지·연락처·요금 내역까지 보여준다. */

export default function BookingTicket({ booking: b, full }: { booking: Booking; full?: boolean }) {
  const d = (s: string) => fmt(parseYmd(s))
  const rows: [string, string][] = [
    ['상태', BOOKING_STATUS[b.status]],
    ['캐리어', `${b.carrierName} · ${b.carrierCode} · ${b.grade} GRADE`],
    ['여행 날짜', `${d(b.startDate)} — ${d(b.endDate)} (${b.nights}박)`],
    ['배송 도착', d(b.deliveryDate)],
    ['문 앞 회수', d(b.pickupDate)],
  ]
  if (full) {
    rows.push(
      ['받는 분', `${b.recipientName} · ${b.phone}`],
      ['주소', `${b.address} ${b.addressDetail}`.trim()],
    )
    if (b.requestMessage) rows.push(['요청 사항', b.requestMessage])
    rows.push(['기본 요금 (2박 3일)', won(b.basePrice)], ['추가 요금', won(b.extraPrice)])
  }
  rows.push(['합계', won(b.totalPrice)])

  return (
    <div className="ticket">
      <div className="top">
        <span className="mono">BOOKING {b.bookingNumber}</span>
        <span className="label">{b.carrierName}</span>
      </div>
      <dl>
        {rows.map(([k, v]) => (
          <div key={k}>
            <dt>{k}</dt>
            <dd>{v}</dd>
          </div>
        ))}
      </dl>
    </div>
  )
}
