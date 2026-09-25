import type { DateRange } from '@/components/Calendar'
import { addDays, fmt } from '@/lib/format'

/* Step 03 배송 정보. 받는 분·연락처는 내 정보로 미리 채워진다. */

export interface DeliveryForm {
  name: string
  phone: string
  addr: string
  addr2: string
  memo: string
}

export const EMPTY_FORM: DeliveryForm = { name: '', phone: '', addr: '', addr2: '', memo: '' }

interface Props {
  form: DeliveryForm
  onChange: (f: DeliveryForm) => void
  range: DateRange
}

export default function DeliveryStep({ form, onChange, range }: Props) {
  const set = (k: keyof DeliveryForm) => (e: { target: { value: string } }) => onChange({ ...form, [k]: e.target.value })
  const arrive = range.start ? fmt(addDays(range.start, -1)) : '출발 전날'
  const collect = range.end ? fmt(range.end) : '반납일'

  return (
    <>
      <h2 className="t-title">어디로 보내드릴까요?</h2>
      <p className="small" style={{ margin: '8px 0 32px' }}>
        캐리어를 받을 주소와 연락처를 입력해주세요.
      </p>
      <div className="row2">
        <label className="field">
          <span className="l">받는 분</span>
          <input value={form.name} onChange={set('name')} placeholder="홍길동" autoComplete="name" />
        </label>
        <label className="field">
          <span className="l">휴대폰 번호</span>
          <input
            value={form.phone}
            onChange={set('phone')}
            placeholder="010-0000-0000"
            inputMode="tel"
            autoComplete="tel"
          />
        </label>
      </div>
      <label className="field">
        <span className="l">주소</span>
        <input value={form.addr} onChange={set('addr')} placeholder="도로명 주소" autoComplete="street-address" />
      </label>
      <label className="field">
        <span className="l">상세 주소</span>
        <input value={form.addr2} onChange={set('addr2')} placeholder="동, 호수" />
      </label>
      <label className="field">
        <span className="l">요청 사항 (선택)</span>
        <textarea value={form.memo} onChange={set('memo')} placeholder="예: 경비실에 맡겨주세요" />
      </label>
      <div className="note">
        <span className="mono">INFO</span>
        <span>
          {arrive} 도착 · {collect} 같은 주소에서 문 앞 회수
        </span>
      </div>
    </>
  )
}
