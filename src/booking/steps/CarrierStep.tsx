import { CarrierMedia } from '@/components/CarrierBag'
import type { Availability, Carrier, CarrierSize } from '@/data/types'
import type { DateRange } from '@/components/Calendar'
import { fmt, nights, priceFor, stayLabel, won } from '@/lib/format'

/* Step 02 캐리어 선택. 날짜가 있으면 그 기간에 예약 가능한 대수(서버 계산)를 보여주고, 0대인 사이즈는 막는다. */

interface Props {
  carriers: Carrier[]
  availability: Availability[] | null
  size: CarrierSize
  range: DateRange
  onPick: (size: CarrierSize) => void
}

export default function CarrierStep({ carriers, availability, size, range, onPick }: Props) {
  const { start, end } = range
  const n = nights(start, end)

  return (
    <>
      <h2 className="t-title">어떤 캐리어가 필요하세요?</h2>
      <p className="small" style={{ margin: '8px 0 32px' }}>
        {start && end ? `${fmt(start)} — ${fmt(end)} · ${stayLabel(n)} 기준 요금입니다.` : '2박 3일 기준 요금입니다.'}
      </p>
      {carriers.map((c) => {
        // 날짜별 가능 대수를 아직 못 받았으면 운영 중인 대수를 보여준다
        const free = availability?.find((a) => a.size === c.key)?.available ?? c.available
        const soldOut = availability !== null && free === 0
        return (
        <button
          key={c.key}
          className={c.key === size ? 'opt on' : 'opt'}
          aria-pressed={c.key === size}
          disabled={soldOut}
          onClick={() => onPick(c.key)}
        >
          <div className="frame" style={{ background: c.bg }}>
            <CarrierMedia carrier={c} handle="down" />
          </div>
          <div>
            <p className="nm">{c.name}</p>
            <p className="small">
              {c.inch} · {c.grade} GRADE · {c.usage}
            </p>
            <p className="small" style={{ color: 'var(--ink)', marginTop: 6 }}>
              {won(priceFor(c, n))}{' '}
              <span className="muted">· {soldOut ? '선택한 기간에 예약 마감' : `${free}대 예약 가능`}</span>
            </p>
          </div>
          <span className="radio" aria-hidden="true" />
        </button>
        )
      })}
    </>
  )
}
