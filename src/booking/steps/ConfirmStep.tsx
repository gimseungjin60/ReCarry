import type { Carrier } from '@/data/types'
import { addDays, fmt, nights, priceFor, stayLabel, won } from '@/lib/format'
import type { DeliveryForm } from './DeliveryStep'

/* Step 04 예약 확인. 1·3단계를 통과해야 들어올 수 있으므로 날짜와 폼은 채워져 있다. */

interface Props {
  carrier: Carrier
  start: Date
  end: Date
  form: DeliveryForm
  agree: boolean
  onAgree: (v: boolean) => void
}

export default function ConfirmStep({ carrier, start, end, form, agree, onAgree }: Props) {
  const n = nights(start, end)
  const rows = [
    ['캐리어', `${carrier.name} · ${carrier.grade} GRADE`],
    ['캐리어 ID', carrier.id],
    ['여행 날짜', `${fmt(start)} — ${fmt(end)} (${stayLabel(n)})`],
    ['배송 도착', fmt(addDays(start, -1))],
    ['문 앞 회수', fmt(end)],
    ['받는 분', `${form.name} · ${form.phone}`],
    ['주소', `${form.addr} ${form.addr2}`],
  ]

  return (
    <>
      <h2 className="t-title">예약 내용을 확인해주세요.</h2>
      <p className="small" style={{ margin: '8px 0 32px' }}>
        취소·환불 정책은 준비 중입니다.
      </p>
      <dl className="sum" style={{ fontSize: 15, marginTop: 0, borderTop: '1px solid var(--ink)' }}>
        {rows.map(([k, v]) => (
          <div key={k}>
            <dt>{k}</dt>
            <dd className={k === '캐리어 ID' ? 'mono' : undefined} style={k === '캐리어 ID' ? { fontSize: 13 } : undefined}>
              {v}
            </dd>
          </div>
        ))}
        <div className="tot">
          <dt>합계</dt>
          <dd>{won(priceFor(carrier, n))}</dd>
        </div>
      </dl>
      <div className="note" style={{ marginTop: 24 }}>
        <span className="mono">PROTOTYPE</span>
        <span>결제 방식은 준비 중입니다. 실제 예약·결제는 발생하지 않습니다.</span>
      </div>
      <label className="agree">
        <input type="checkbox" checked={agree} onChange={(e) => onAgree(e.target.checked)} />
        <span>프로토타입 예약임을 확인했습니다.</span>
      </label>
    </>
  )
}
