import { useState } from 'react'
import type { CSSProperties } from 'react'
import { fmt } from '@/lib/format'
import './Calendar.css'

/* prototype calendar() / monthHTML() 이식. 오늘 이전(오늘 포함)은 선택할 수 없다.
   범위 상태는 부모가 가진다 — Product 는 로컬, Booking 은 Step 4 에서 연결. */

export interface DateRange {
  start: Date | null
  end: Date | null
}

const DAYS = ['일', '월', '화', '수', '목', '금', '토']

function today() {
  const d = new Date()
  d.setHours(0, 0, 0, 0)
  return d
}

/** prototype pickDay(): 시작 → 끝 순서로 고르고, 시작보다 이른 날을 누르면 시작을 옮긴다 */
function pick({ start, end }: DateRange, d: Date): DateRange {
  if (!start || end) return { start: d, end: null }
  if (d <= start) return { start: d, end: null }
  return { start, end: d }
}

interface Props {
  value: DateRange
  onChange: (r: DateRange) => void
  /** 한 번에 보여줄 달 수. 모바일에서는 CSS 가 1개로 줄인다. */
  months?: number
}

export default function Calendar({ value, onChange, months = 1 }: Props) {
  const t = today()
  // 20일 이후면 다음 달부터 보여준다
  const [base, setBase] = useState(() => new Date(t.getFullYear(), t.getMonth() + (t.getDate() > 20 ? 1 : 0), 1))
  const atMin = base.getFullYear() === t.getFullYear() && base.getMonth() === t.getMonth()
  const { start, end } = value

  const status = !start ? '출발일을 선택하세요' : !end ? '도착일을 선택하세요' : `${fmt(start)} — ${fmt(end)}`
  const shift = (n: number) => setBase(new Date(base.getFullYear(), base.getMonth() + n, 1))

  return (
    <div className="cal">
      <div className="cal-top">
        <button onClick={() => shift(-1)} aria-label="이전 달" disabled={atMin} style={atMin ? { opacity: 0.3 } : undefined}>
          ‹
        </button>
        <span className="small" style={{ color: 'var(--ink)' }}>
          {status}
        </span>
        <button onClick={() => shift(1)} aria-label="다음 달">
          ›
        </button>
      </div>

      <div className="cal-months" style={{ '--m': months } as CSSProperties}>
        {Array.from({ length: months }, (_, i) => {
          const y = base.getFullYear()
          const m = base.getMonth() + i
          const first = new Date(y, m, 1)
          const count = new Date(y, m + 1, 0).getDate()
          return (
            <div className="cal-m" key={i}>
              <h4>
                {first.getFullYear()}년 {first.getMonth() + 1}월
              </h4>
              <div className="cal-g">
                {DAYS.map((d) => (
                  <span className="wd" key={d}>
                    {d}
                  </span>
                ))}
                {Array.from({ length: first.getDay() }, (_, k) => (
                  <span key={'e' + k} />
                ))}
                {Array.from({ length: count }, (_, k) => {
                  const dt = new Date(y, m, k + 1)
                  const cls = []
                  if (start && +dt === +start) cls.push(...(end ? ['s'] : ['s', 'e']))
                  if (end && +dt === +end) cls.push('e')
                  if (start && end && dt > start && dt < end) cls.push('in')
                  if (+dt === +t) cls.push('today')
                  return (
                    <button
                      key={k}
                      className={cls.join(' ') || undefined}
                      disabled={dt <= t}
                      aria-label={fmt(dt)}
                      aria-pressed={cls.includes('s') || cls.includes('e')}
                      onClick={() => onChange(pick(value, dt))}
                    >
                      <span>{k + 1}</span>
                    </button>
                  )
                })}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
