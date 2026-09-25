import { useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'
import Button from '@/components/Button'
import SectionHeader from '@/components/SectionHeader'
import CarrierPassport from '@/components/CarrierPassport'
import ProductGallery from '@/components/ProductGallery'
import BeforeAfter from '@/components/BeforeAfter'
import CarrierTimeline from '@/components/CarrierTimeline'
import Calendar from '@/components/Calendar'
import type { DateRange } from '@/components/Calendar'
import { COMMON_SPEC, GRADE_SPEC, HOW_TO_USE, INSPECTION_CHECKS, isCarrierSize } from '@/data/carriers'
import { useCatalog } from '@/lib/catalog'
import type { Carrier } from '@/data/types'
import { fmt, nights, priceFor, stayLabel, won } from '@/lib/format'
import { useReveal } from '@/lib/motion'

/* PRD §4 Product Detail — prototype productHTML() 구조 그대로. */

/** 패널 안 날짜 선택. 고른 날짜는 예약하기로 Booking 에 넘어간다. */
function DateBox({ carrier, range, setRange }: { carrier: Carrier; range: DateRange; setRange: (r: DateRange) => void }) {
  const [open, setOpen] = useState(false)
  const { start, end } = range
  const n = nights(start, end)

  const change = (r: DateRange) => {
    setRange(r)
    if (r.start && r.end) setOpen(false)
  }

  return (
    <div className="datebox">
      <button className="datebox-btn" aria-expanded={open} onClick={() => setOpen(!open)}>
        <span>
          <span className="label muted" style={{ display: 'block' }}>
            여행 날짜
          </span>
          <span className="v">{start && end ? `${fmt(start)} — ${fmt(end)}` : '여행 날짜 선택'}</span>
        </span>
        <span aria-hidden="true" style={{ fontSize: 20 }}>
          {open ? '−' : '+'}
        </span>
      </button>
      {open && (
        <div className="cal-pop">
          <Calendar value={range} onChange={change} />
        </div>
      )}
      {n > 0 && (
        <div className="pd-total" style={{ padding: '0 18px' }}>
          <span className="muted">{stayLabel(n)}</span>
          <b style={{ fontWeight: 500 }}>{won(priceFor(carrier, n))}</b>
        </div>
      )}
    </div>
  )
}

export default function Product() {
  const { size } = useParams()
  const navigate = useNavigate()
  // prototype 처럼 사이즈를 옮겨 다녀도 고른 날짜는 유지한다
  const [range, setRange] = useState<DateRange>({ start: null, end: null })
  const catalog = useCatalog()
  const p = isCarrierSize(size) ? catalog.bySize(size) : undefined
  useReveal(p)

  // 없는 사이즈로 들어오면(또는 카탈로그에 없으면) 조용히 컬렉션으로 되돌린다
  if (!isCarrierSize(size) || (catalog.list && !p)) {
    return <Navigate to="/" state={{ anchor: 'collection' }} replace />
  }
  if (!p) {
    return (
      <div className="wrap pd-loading" aria-busy="true">
        {catalog.error ? (
          <p className="note" role="alert">
            <span>{catalog.error}</span>
            <button className="link" onClick={catalog.retry}>
              다시 불러오기
            </button>
          </p>
        ) : (
          <p className="small">캐리어 정보를 불러오는 중입니다.</p>
        )}
      </div>
    )
  }
  // 보고 있던 캐리어(Carrier ID)를 우선 배정해 달라고 함께 넘긴다
  const book = () => navigate('/booking', { state: { size: p.key, carrierCode: p.id, ...range } })

  const spec = [
    { k: '사이즈', v: p.inch },
    { k: '크기', v: p.dims },
    { k: '용량', v: p.capacity },
    { k: '무게', v: p.weight },
    ...COMMON_SPEC,
    { k: '등급', v: `${p.grade} GRADE — ${GRADE_SPEC[p.grade]}` },
  ]

  return (
    <>
      <div className="wrap">
        <div className="crumb">
          <Link to="/" state={{ anchor: 'collection' }}>
            Collection
          </Link>
          <span>/</span>
          <span style={{ color: 'var(--ink)' }}>{p.name}</span>
        </div>

        {/* key: 다른 사이즈로 옮기면 갤러리를 첫 이미지로 되돌린다 */}
        <div className="grid pd" key={p.key}>
          <ProductGallery carrier={p} />

          <aside className="pd-panel">
            <p className="mono muted">{p.id} · 이용 가능</p>
            <h1 className="d-m">{p.name}</h1>
            <div className="pd-meta">
              <span style={{ fontSize: 15 }}>{p.inch}</span>
              <span className="tag">{p.grade} Grade</span>
            </div>
            <p className="small" style={{ marginTop: 12 }}>
              {p.usage} · {p.capacity}
            </p>

            <div className="pd-price">
              <span className="p">{won(p.price)}</span>
              <span className="small">
                / 2박 3일 · 1박 추가 {won(p.extra)}
              </span>
            </div>

            <DateBox carrier={p} range={range} setRange={setRange} />
            <Button block arrow onClick={book} style={{ marginTop: 12 }}>
              예약하기
            </Button>
            <p className="small" style={{ marginTop: 12 }}>
              출발 전날 문 앞 배송 · 반납일 문 앞 회수
            </p>

            <ul className="checks" aria-label="검수 상태">
              {INSPECTION_CHECKS.map((c) => (
                <li key={c}>
                  <span>
                    <span className="ck" aria-hidden="true">
                      ✓
                    </span>
                    {c}
                  </span>
                  <span className="ok">PASS</span>
                </li>
              ))}
            </ul>
            <p className="mono muted" style={{ marginTop: 12 }}>
              INSPECTED {p.inspectedAt} · {INSPECTION_CHECKS.length} / {INSPECTION_CHECKS.length} CHECKS
            </p>
          </aside>
        </div>

        <section className="pd-sec">
          <div className="grid" data-reveal>
            <div style={{ gridColumn: '1/6' }}>
              <SectionHeader eyebrow="Product" title={p.headline} size="d-m" />
              <p className="muted" style={{ marginTop: 20, maxWidth: 420 }}>
                {p.desc}
              </p>
            </div>
            <dl className="spec">
              {spec.map((s) => (
                <div key={s.k}>
                  <dt>{s.k}</dt>
                  <dd>{s.v}</dd>
                </div>
              ))}
            </dl>
          </div>
        </section>

        <section className="pd-sec">
          <div className="grid story-body" style={{ marginTop: 0 }} key={p.key} data-reveal>
            <BeforeAfter carrier={p} />
            <div className="passport">
              <SectionHeader
                eyebrow="The story of this carrier"
                title={[`${p.collectedFrom}에서`, '다시 여행으로.']}
                size="d-m"
              />
              <div style={{ marginTop: 32 }}>
                <CarrierPassport carrier={p} />
              </div>
              <CarrierTimeline story={p.story} light />
            </div>
          </div>
        </section>

        <section className="pd-sec">
          <SectionHeader eyebrow="How to use" title={['이용 방법']} size="d-m" />
          <div className="steps4" data-reveal="stagger">
            {HOW_TO_USE.map((s, i) => (
              <div key={s.t}>
                <span className="n">0{i + 1}</span>
                <p className="t">{s.t}</p>
                <p className="small">{s.s}</p>
              </div>
            ))}
          </div>
        </section>

        <section className="pd-sec" style={{ paddingBottom: 'var(--space-10)' }}>
          <div className="pd-end" data-reveal>
            <h2 className="d-m">
              <span className="mono" style={{ fontSize: '.6em', letterSpacing: 0 }}>
                {p.id}
              </span>
              의<br />
              다음 여행을 시작하세요.
            </h2>
            <Button arrow onClick={book}>
              예약하기
            </Button>
          </div>
        </section>
      </div>

      <div className="pd-bar">
        <div>
          <div className="p">{won(p.price)}</div>
          <div className="small">2박 3일 기준</div>
        </div>
        <Button onClick={book}>예약하기</Button>
      </div>
    </>
  )
}
