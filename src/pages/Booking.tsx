import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import Button from '@/components/Button'
import BookingStepper from '@/components/BookingStepper'
import BookingSummary from '@/components/BookingSummary'
import { CARRIERS, DEFAULT_SIZE, isCarrierSize } from '@/data/carriers'
import { BOOKING_STEPS } from '@/data/process'
import type { BookingStep } from '@/data/process'
import { nights, priceFor, stayLabel, won } from '@/lib/format'

/* PRD §4 Booking — 4단계 뼈대.
   각 단계의 내용(캘린더 / 캐리어 선택 / 배송 폼 / 확인)은 Step 3·4 에서 채운다.
   예약 상태는 아직 이 페이지 안에만 있다. 여러 페이지가 공유해야 할 때 올린다. */

const STEP_INTRO: Record<BookingStep, { title: string; desc: string }> = {
  1: {
    title: '언제 여행을 떠나시나요?',
    desc: '출발일과 도착일을 선택하세요. 기본 요금은 2박 3일 기준이며, 캐리어는 출발 전날 도착합니다.',
  },
  2: { title: '어떤 캐리어가 필요하세요?', desc: '2박 3일 기준 요금입니다.' },
  3: { title: '어디로 보내드릴까요?', desc: '회원가입 없이, 배송에 필요한 정보만 받습니다.' },
  4: { title: '예약 내용을 확인해주세요.', desc: '배송 전까지는 언제든 무료로 취소할 수 있습니다.' },
}

const NEXT_LABEL = ['캐리어 선택', '배송 정보 입력', '예약 확인', '예약 확정']

export default function Booking() {
  const navigate = useNavigate()
  const { state } = useLocation()

  const fromProduct = (state as { size?: string } | null)?.size
  // 캐리어 변경(Step 02)은 다음 단계에서 setter 를 추가한다
  const [size] = useState(isCarrierSize(fromProduct) ? fromProduct : DEFAULT_SIZE)
  const [step, setStep] = useState<BookingStep>(1)
  const [maxStep, setMaxStep] = useState<BookingStep>(1)

  // Step 3 에서 캘린더가 붙기 전까지는 날짜가 비어 있다
  const [start] = useState<Date | null>(null)
  const [end] = useState<Date | null>(null)

  const carrier = CARRIERS[size]
  const n = nights(start, end)
  const intro = STEP_INTRO[step]

  const goto = (s: BookingStep) => {
    setStep(s)
    setMaxStep((m) => (s > m ? s : m))
    window.scrollTo(0, 0)
  }

  const next = () => {
    if (step < 4) {
      goto((step + 1) as BookingStep)
      return
    }
    const d = new Date()
    const code = `RC-B-${String(d.getFullYear()).slice(2)}${String(d.getMonth() + 1).padStart(2, '0')}${String(
      d.getDate(),
    ).padStart(2, '0')}-${carrier.id.slice(-4)}`
    navigate('/booking/complete', {
      replace: true,
      state: { size: carrier.key, total: priceFor(carrier, n), code },
    })
  }

  return (
    <>
      <div className="wrap">
        <div className="bk-top">
          <p className="label muted">Reservation</p>
          <h1 className="d-m" style={{ marginTop: 12 }}>
            예약하기
          </h1>
          <BookingStepper step={step} maxStep={maxStep} onGoto={goto} />
        </div>

        <div className="grid bk">
          <div className="bk-main">
            <h2 className="t-title">{intro.title}</h2>
            <p className="small" style={{ margin: '8px 0 32px' }}>
              {intro.desc}
            </p>

            {/* 단계별 실제 입력 UI 는 Step 3·4 에서 들어온다 */}
            <div className="note">
              <span className="mono">STEP 0{step}</span>
              <span>
                {BOOKING_STEPS[step - 1]} · {carrier.name}
                {n > 0 && ` · ${stayLabel(n)}`}
              </span>
            </div>

            <div className="bk-nav">
              {step > 1 ? (
                <button className="link" onClick={() => goto((step - 1) as BookingStep)}>
                  이전 단계
                </button>
              ) : (
                <span />
              )}
              <Button arrow onClick={next}>
                {NEXT_LABEL[step - 1]}
              </Button>
            </div>
          </div>

          <BookingSummary carrier={carrier} start={start} end={end} />
        </div>
      </div>

      <div className="bk-bar">
        <div className="tt">
          <b>{n ? won(priceFor(carrier, n)) : won(carrier.price) + '~'}</b>
          {carrier.name}
          {n > 0 && ` · ${stayLabel(n)}`}
        </div>
        {step > 1 && (
          <Button variant="secondary" small onClick={() => goto((step - 1) as BookingStep)} aria-label="이전 단계">
            ←
          </Button>
        )}
        <Button small onClick={next}>
          {NEXT_LABEL[step - 1]}
        </Button>
      </div>
    </>
  )
}
