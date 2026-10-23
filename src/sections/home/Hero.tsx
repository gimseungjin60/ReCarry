import { useEffect, useRef, useState } from 'react'
import type { CSSProperties } from 'react'
import { useNavigate } from 'react-router-dom'
import Button from '@/components/Button'
import Calendar from '@/components/Calendar'
import type { DateRange } from '@/components/Calendar'
import CarrierBag from '@/components/CarrierBag'
import { DEFAULT_SIZE, PRESENTATION } from '@/data/carriers'
import type { Carrier, CarrierSize } from '@/data/types'
import { useCatalog } from '@/lib/catalog'
import { fmt, nights, priceFor, stayLabel, won } from '@/lib/format'

/* Home 첫 화면 — 무엇을(검수된 캐리어) · 얼마에(~부터) · 어떻게 받는지(출발 전날 배송)를 한 화면에.
   사이즈와 날짜만 고르면 바로 예약 흐름으로 넘어간다. 가격은 서버가 다시 계산한다 (표시용).
   오른쪽 캐리어는 고른 사이즈의 크기 · 색으로 바뀐다. */

/** 가장 큰 사이즈(28") 대비 화면 높이 비율 */
const TALLEST = Math.max(...Object.values(PRESENTATION).map((p) => p.bagHeight))

interface BookingProps {
  list: Carrier[] | null
  size: CarrierSize
  onSize: (s: CarrierSize) => void
}

function HeroBooking({ list, size, onSize }: BookingProps) {
  const navigate = useNavigate()
  const [range, setRange] = useState<DateRange>({ start: null, end: null })
  const [open, setOpen] = useState(false)
  const box = useRef<HTMLDivElement>(null)
  const carrier = list?.find((c) => c.key === size)
  const n = nights(range.start, range.end)

  // 바깥을 누르면 달력을 닫는다
  useEffect(() => {
    if (!open) return
    const close = (e: MouseEvent) => !box.current?.contains(e.target as Node) && setOpen(false)
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [open])

  const book = () => {
    if (!range.start || !range.end) return setOpen(true)
    navigate('/booking', { state: { size, start: range.start, end: range.end } })
  }

  return (
    <div className="hbook" ref={box}>
      <div className="hbook-field">
        <span className="hbook-label">사이즈</span>
        <div className="hbook-sizes" role="radiogroup" aria-label="사이즈">
          {(list ?? []).map((c) => (
            <button key={c.key} type="button" role="radio" aria-checked={size === c.key} onClick={() => onSize(c.key)}>
              <b>{c.key}"</b>
              <span>{c.usage.split('·')[0].trim()}</span>
            </button>
          ))}
        </div>
      </div>
      <button type="button" className="hbook-field hbook-date" aria-expanded={open} onClick={() => setOpen(!open)}>
        <span className="hbook-label">여행 날짜</span>
        <span className="v">{range.start && range.end ? `${fmt(range.start)} — ${fmt(range.end)}` : '출발일 · 도착일 선택'}</span>
        <span className="hint">{n > 0 ? stayLabel(n) : '출발 전날 문 앞 도착'}</span>
      </button>
      <Button arrow onClick={book} className="hbook-go">
        {carrier && n > 0 ? won(priceFor(carrier, n)) : '예약하기'}
      </Button>
      {open && (
        <div className="hbook-cal">
          <Calendar
            value={range}
            onChange={(r) => {
              setRange(r)
              if (r.start && r.end) setOpen(false)
            }}
          />
        </div>
      )}
    </div>
  )
}

export default function Hero() {
  const { list } = useCatalog()
  const [size, setSize] = useState<CarrierSize>(DEFAULT_SIZE)
  const shown = list?.find((c) => c.key === size)
  const look = PRESENTATION[size]
  const from = list?.length ? Math.min(...list.map((c) => c.price)) : null
  const checks = shown?.inspection ?? []

  return (
    <section className="hero3" id="top" aria-label="RECARRY 소개">
      <div className="wrap grid hero3-in">
        <div className="hero3-copy">
          <p className="label">Carrier rental · Second journey</p>
          <h1 className="d-xl">
            검수 끝난 캐리어,
            <br />
            출발 전날 문 앞으로.
          </h1>
          <p className="lead">
            버려지는 캐리어를 다시 여행으로. 세척 · 수리 · 검수를 마친 캐리어를 여행 기간만큼만 빌려 쓰세요.
          </p>
          <HeroBooking list={list} size={size} onSize={setSize} />
          <ul className="hero3-chips">
            {from !== null && <li>2박 3일 {won(from)}부터</li>}
            <li>출발 전날 도착</li>
            <li>반납일 문 앞 회수</li>
          </ul>
        </div>
        <div className="hero3-art" aria-hidden="true">
          {/* 사이즈별 실제 크기 비율로 높이가 바뀐다 (CSS transition) */}
          <div className="hero3-stage" style={{ '--h': look.bagHeight / TALLEST } as CSSProperties}>
            <div className="hero3-float">
              <CarrierBag color={look.color} width={look.bagWidth} height={look.bagHeight} tag ground={false} />
            </div>
            <div className="hero3-ground" />
          </div>
          {shown && (
            <p className="mono hero3-id">
              {shown.id} · {shown.grade} GRADE
              {checks.length > 0 && ` · 검수 ${checks.filter((c) => c.passed).length}/${checks.length}`}
            </p>
          )}
        </div>
      </div>
    </section>
  )
}
