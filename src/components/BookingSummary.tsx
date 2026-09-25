import { CarrierMedia } from './CarrierBag'
import { fmt, nights, priceFor, stayLabel, won } from '@/lib/format'
import type { Carrier } from '@/data/types'
import './BookingSummary.css'

/* 예약 사이드 요약. 날짜가 아직 없으면 '—' 로 둔다 (prototype 과 동일). */

interface Props {
  carrier: Carrier
  start: Date | null
  end: Date | null
}

export default function BookingSummary({ carrier, start, end }: Props) {
  const n = nights(start, end)

  return (
    <aside className="bk-side">
      <div className="frame" style={{ background: carrier.bg }}>
        <CarrierMedia carrier={carrier} handle="down" />
      </div>

      <p className="mono muted" style={{ marginTop: 16 }}>
        {carrier.id}
      </p>
      <p className="t-title" style={{ marginTop: 2 }}>
        {carrier.name}
      </p>

      <dl className="sum">
        <div>
          <dt>등급</dt>
          <dd>{carrier.grade} GRADE</dd>
        </div>
        <div>
          <dt>여행 날짜</dt>
          <dd>
            {start && end ? (
              <>
                {fmt(start)} —<br />
                {fmt(end)}
              </>
            ) : (
              '—'
            )}
          </dd>
        </div>
        <div>
          <dt>기간</dt>
          <dd>{n ? stayLabel(n) : '—'}</dd>
        </div>
        <div>
          <dt>기본 요금 (2박 3일)</dt>
          <dd>{won(carrier.price)}</dd>
        </div>
        <div>
          <dt>추가 {Math.max(0, n - 2)}박</dt>
          <dd>{won(Math.max(0, n - 2) * carrier.extra)}</dd>
        </div>
        <div>
          <dt>배송 · 회수</dt>
          <dd>준비 중</dd>
        </div>
        <div className="tot">
          <dt>합계</dt>
          <dd>{n ? won(priceFor(carrier, n)) : won(carrier.price) + '~'}</dd>
        </div>
      </dl>
    </aside>
  )
}
