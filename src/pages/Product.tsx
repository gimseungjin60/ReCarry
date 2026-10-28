import { useEffect, useRef, useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'
import Button from '@/components/Button'
import SectionHeader from '@/components/SectionHeader'
import CarrierPassport from '@/components/CarrierPassport'
import ProductGallery from '@/components/ProductGallery'
import BeforeAfter from '@/components/BeforeAfter'
import CarrierTimeline from '@/components/CarrierTimeline'
import Calendar from '@/components/Calendar'
import type { DateRange } from '@/components/Calendar'
import { COMMON_SPEC, GRADE_SPEC, HOW_TO_USE, inspectionLabel, isCarrierSize } from '@/data/carriers'
import { FAQ } from '@/data/home'
import { FaqList } from '@/sections/home/Faq'
import { api } from '@/lib/api'
import { useCatalog } from '@/lib/catalog'
import type { Availability, Carrier } from '@/data/types'
import { addDays, fmt, nights, priceFor, stayLabel, won, ymd } from '@/lib/format'
import { useReveal } from '@/lib/motion'
import { useTitle } from '@/lib/useTitle'

/* PRD §4 Product Detail — prototype productHTML() 구조 그대로. */

/** 패널 안 날짜 선택. 고른 날짜는 예약하기로 Booking 에 넘어간다. 열림 상태는 예약하기 버튼도 쓴다 */
function DateBox({
  carrier,
  range,
  setRange,
  avail,
  open,
  setOpen,
}: {
  carrier: Carrier
  range: DateRange
  setRange: (r: DateRange) => void
  avail: Availability | null
  open: boolean
  setOpen: (v: boolean) => void
}) {
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
      {n > 0 && start && end && (
        <dl className="sum pd-sum">
          <div>
            <dt>대여 요금 · {stayLabel(n)}</dt>
            <dd>{won(priceFor(carrier, n))}</dd>
          </div>
          {avail && (
            <div>
              <dt>보증금 (반납 후 환불)</dt>
              <dd>{won(avail.deposit)}</dd>
            </div>
          )}
          <div>
            <dt>도착 · 회수</dt>
            <dd>
              {fmt(addDays(start, -1))} · {fmt(end)}
            </dd>
          </div>
          {avail && (
            <div className="tot">
              <dt>결제 예정 금액</dt>
              <dd>{won(priceFor(carrier, n) + avail.deposit)}</dd>
            </div>
          )}
        </dl>
      )}
    </div>
  )
}

export default function Product() {
  const { size } = useParams()
  useTitle(isCarrierSize(size) ? `RECARRY ${size}인치` : undefined)
  const navigate = useNavigate()
  // prototype 처럼 사이즈를 옮겨 다녀도 고른 날짜는 유지한다
  const [range, setRange] = useState<DateRange>({ start: null, end: null })
  const [dateOpen, setDateOpen] = useState(false)
  const dateRef = useRef<HTMLDivElement>(null)
  const catalog = useCatalog()
  const p = isCarrierSize(size) ? catalog.bySize(size) : undefined
  useReveal(p)
  // 날짜를 고르면 그 기간의 대여 가능 대수 · 보증금 (서버 계산)
  const [avail, setAvail] = useState<Availability[] | null>(null)
  const { start, end } = range
  useEffect(() => {
    setAvail(null)
    if (!start || !end) return
    let alive = true
    api<Availability[]>(`/api/carriers/availability?start=${ymd(start)}&end=${ymd(end)}`)
      .then((a) => alive && setAvail(a))
      .catch(() => {})
    return () => {
      alive = false
    }
  }, [start, end])

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
  // 보고 있던 캐리어(Carrier ID)를 우선 배정해 달라고 함께 넘긴다.
  // 날짜를 아직 안 골랐으면 예약 화면으로 넘기지 않고 이 화면의 달력을 연다 (홈 첫 화면과 같은 동작)
  const book = () => {
    if (!start || !end) {
      setDateOpen(true)
      dateRef.current?.scrollIntoView({ block: 'center', behavior: 'smooth' })
      return
    }
    navigate('/booking', { state: { size: p.key, carrierCode: p.id, start, end } })
  }
  const mine = avail?.find((a) => a.size === p.key) ?? null
  const n = nights(start, end)
  const soldOut = mine?.available === 0
  // 날짜를 고르면 "그 기간" 재고로, 아니면 운영 중인 대수로 알려준다
  const status = mine
    ? soldOut
      ? '이 기간에는 예약 가능한 캐리어가 없습니다'
      : `이 기간 ${mine.available}대 예약 가능`
    : p.available > 0
      ? '이용 가능'
      : '지금은 예약 가능한 캐리어가 없습니다'

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
            <p className="mono muted">
              {p.id} · {status}
            </p>
            <h1 className="d-m">{p.name}</h1>
            <div className="pd-meta">
              <span style={{ fontSize: 15 }}>{p.inch}</span>
              <span className="tag">{p.grade} Grade</span>
            </div>
            <p className="small" style={{ marginTop: 12 }}>
              {p.usage} · {p.capacity}
            </p>

            {/* 옵션 선택 — 사이즈를 바꿔도 고른 날짜는 유지된다 */}
            <div className="pd-sizes" role="radiogroup" aria-label="사이즈">
              {(catalog.list ?? []).map((c) => (
                <Link key={c.key} to={`/product/${c.key}`} role="radio" aria-checked={c.key === p.key} replace>
                  <b>{c.key}"</b>
                  <span>{won(c.price)}~</span>
                </Link>
              ))}
            </div>

            <div className="pd-price">
              <span className="p">{won(p.price)}</span>
              <span className="small">
                / 2박 3일 · 1박 추가 {won(p.extra)}
              </span>
            </div>

            <div ref={dateRef}>
              <DateBox carrier={p} range={range} setRange={setRange} avail={mine} open={dateOpen} setOpen={setDateOpen} />
            </div>
            <Button block arrow onClick={book} style={{ marginTop: 12 }} disabled={soldOut}>
              {soldOut ? '이 기간에는 예약할 수 없습니다' : '예약하기'}
            </Button>
            {soldOut && (
              <p className="small" style={{ marginTop: 8 }}>
                다른 날짜나 사이즈를 골라주세요.
              </p>
            )}
            {/* 쇼핑몰 구매 버튼 아래의 혜택 · 안심 문구 자리 — 대여 정책 범위 안에서만 */}
            <ul className="pd-trust">
              <li>출발 전날 문 앞 배송 · 반납일 문 앞 회수</li>
              <li>입금 전 무료 취소 · 배송 3일 전까지 전액 환불</li>
              <li>보증금은 반납 검수 후 영업일 3일 안에 반환</li>
            </ul>

            {/* 대표 캐리어(Carrier ID)의 실제 검수 기록. 기록이 없으면 통과로 꾸미지 않는다 */}
            {p.inspection.length ? (
              <>
                <ul className="checks" aria-label="검수 상태">
                  {p.inspection.map((c) => (
                    <li key={c.item}>
                      <span>
                        <span className={c.passed ? 'ck' : 'ck ng'} aria-hidden="true">
                          {c.passed ? '✓' : '!'}
                        </span>
                        {inspectionLabel(c.item)}
                        {c.note && <span className="small"> · {c.note}</span>}
                      </span>
                      <span className={c.passed ? 'ok' : 'ok ng'}>{c.passed ? 'PASS' : '재점검'}</span>
                    </li>
                  ))}
                </ul>
                <p className="mono muted" style={{ marginTop: 12 }}>
                  INSPECTED {p.inspectedAt} · {p.inspection.filter((c) => c.passed).length} / {p.inspection.length} CHECKS
                </p>
              </>
            ) : null}
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

        <section className="pd-sec faq">
          <div className="grid">
            <div className="faq-l">
              <SectionHeader eyebrow="FAQ" title={['빌리기 전에', '궁금한 것들.']} size="d-m" />
              <p className="small" style={{ marginTop: 20 }}>
                더 궁금하면{' '}
                <Link className="link" to="/help">
                  고객센터
                </Link>
              </p>
            </div>
            <div className="faq-r">
              <FaqList items={FAQ} />
            </div>
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
            <Button arrow onClick={book} disabled={soldOut}>
              {soldOut ? '이 기간에는 예약할 수 없습니다' : '예약하기'}
            </Button>
          </div>
        </section>
      </div>

      <div className="pd-bar">
        <div>
          <div className="p">{n > 0 ? won(priceFor(p, n) + (mine?.deposit ?? 0)) : won(p.price)}</div>
          <div className="small">{n > 0 ? (mine ? `결제 예정 · ${stayLabel(n)}` : `대여 요금 · ${stayLabel(n)}`) : '2박 3일 기준'}</div>
        </div>
        <Button onClick={book} disabled={soldOut}>
          {soldOut ? '예약 마감' : '예약하기'}
        </Button>
      </div>
    </>
  )
}
