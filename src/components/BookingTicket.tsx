import { BOOKING_STATUS, CANCEL_REASON } from '@/data/status'
import type { Booking } from '@/data/types'
import { dateTime, fmt, parseYmd, stayLabel, won } from '@/lib/format'

/* 예약 티켓 (prototype 의 done 화면 .ticket). Complete · My Page 상세 · Admin 상세가 같이 쓴다.
   full 이면 배송지·연락처·요금 내역까지 보여준다. */

export default function BookingTicket({ booking: b, full }: { booking: Booking; full?: boolean }) {
  const d = (s: string) => fmt(parseYmd(s))
  const rows: [string, string][] = [
    ['상태', BOOKING_STATUS[b.status] + (b.cancelReason ? ` · ${CANCEL_REASON[b.cancelReason]}` : '')],
    ['캐리어', `${b.carrierName} · ${b.carrierCode} · ${b.grade} GRADE`],
    ['여행 날짜', `${d(b.startDate)} — ${d(b.endDate)} (${stayLabel(b.nights)})`],
    ['배송 도착', d(b.deliveryDate)],
    ['문 앞 회수', d(b.pickupDate)],
  ]
  if (b.expiresAt)
    rows.push(['입금 기한', `${dateTime(b.expiresAt)} (지나면 자동 취소)`])
  if (full) {
    rows.push(
      ['받는 분', `${b.recipientName} · ${b.phone}`],
      ['주소', `${b.address} ${b.addressDetail}`.trim()],
    )
    if (b.requestMessage) rows.push(['요청 사항', b.requestMessage])
    rows.push(['기본 요금 (2박 3일)', won(b.basePrice)], ['추가 요금', won(b.extraPrice)])
  }
  rows.push(['대여 요금', won(b.totalPrice)])
  // V4 이전 예약은 보증금 기록이 없다
  if (b.deposit > 0) rows.push(['보증금 (반납 후 환불)', won(b.deposit)], ['합계 (대여 요금 + 보증금)', won(b.amountDue)])
  if (b.paidAt && b.paidAmount != null)
    rows.push(['입금 확인', [won(b.paidAmount), dateTime(b.paidAt), b.payerName].filter(Boolean).join(' · ')])
  if (b.refundAmount != null)
    rows.push(['환불', `${won(b.refundAmount)} · ${b.refundedAt ? `${dateTime(b.refundedAt)} 송금 완료` : '송금 예정 (영업일 3일 안)'}`])
  if (b.depositSettledAt && b.depositDeduction != null)
    rows.push([
      '보증금 정산',
      `공제 ${won(b.depositDeduction)} · 돌려드림 ${won(b.deposit - b.depositDeduction)}` + (b.depositNote ? ` (${b.depositNote})` : ''),
    ])

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
