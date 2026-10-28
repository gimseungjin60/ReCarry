import { CarrierMedia } from './CarrierBag'
import { addDays, fmt, nights, priceFor, stayLabel, won } from '@/lib/format'
import type { Carrier } from '@/data/types'
import './BookingSummary.css'

/* 예약 사이드 요약. 날짜가 아직 없으면 '—' 로 둔다 (prototype 과 동일). */

interface Props {
  carrier: Carrier
  start: Date | null
  end: Date | null
  /** 서버가 계산한 보증금 (그 기간 availability). 아직 모르면 null */
  deposit: number | null
}

export default function BookingSummary({ carrier, start, end, deposit }: Props) {
  const n = nights(start, end)
  // 날짜와 보증금을 알면 상품 화면 · 예약 확인과 같은 "결제 예정 금액"을 합계로 보여준다
  const dep = n > 0 ? deposit : null

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
          <dt>도착 · 회수</dt>
          <dd>{start && end ? `${fmt(addDays(start, -1))} · ${fmt(end)}` : '—'}</dd>
        </div>
        <div className={dep === null ? 'tot' : undefined}>
          <dt>대여 요금</dt>
          <dd>{n ? won(priceFor(carrier, n)) : won(carrier.price) + '~'}</dd>
        </div>
        <div>
          <dt>보증금 (반납 후 환불)</dt>
          <dd>{dep === null ? '대여 요금의 30%' : won(dep)}</dd>
        </div>
        {dep !== null && (
          <div className="tot">
            <dt>결제 예정 금액</dt>
            <dd>{won(priceFor(carrier, n) + dep)}</dd>
          </div>
        )}
      </dl>
    </aside>
  )
}
