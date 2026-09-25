import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import Button from '@/components/Button'
import BookingStepper from '@/components/BookingStepper'
import BookingSummary from '@/components/BookingSummary'
import type { DateRange } from '@/components/Calendar'
import DateStep from '@/booking/steps/DateStep'
import CarrierStep from '@/booking/steps/CarrierStep'
import DeliveryStep, { EMPTY_FORM } from '@/booking/steps/DeliveryStep'
import ConfirmStep from '@/booking/steps/ConfirmStep'
import { DEFAULT_SIZE, isCarrierSize } from '@/data/carriers'
import type { BookingStep } from '@/data/process'
import type { Availability, Booking as BookingResult, CarrierSize } from '@/data/types'
import { nights, priceFor, stayLabel, won, ymd } from '@/lib/format'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { useCatalog } from '@/lib/catalog'

/* PRD §4 Booking — 로그인한 사용자만 들어온다 (App 의 RequireAuth).
   Product 에서 사이즈·날짜·보던 Carrier ID 를 받아 오며, 날짜가 있으면 prototype 처럼 Step 02 부터 시작한다.
   확정하면 POST /api/bookings — 금액·재고·날짜는 서버가 다시 검증한다. */

const NEXT_LABEL = ['캐리어 선택', '배송 정보 입력', '예약 확인', '예약 확정']

interface FromProduct {
  size?: string
  carrierCode?: string
  start?: unknown
  end?: unknown
}

const asDate = (v: unknown) => (v instanceof Date ? v : null)

export default function Booking() {
  const navigate = useNavigate()
  const from = (useLocation().state ?? {}) as FromProduct
  const { user } = useAuth()
  const catalog = useCatalog()

  const [size, setSize] = useState<CarrierSize>(isCarrierSize(from.size) ? from.size : DEFAULT_SIZE)
  const [range, setRange] = useState<DateRange>(() => {
    const start = asDate(from.start)
    const end = asDate(from.end)
    return start && end && end > start ? { start, end } : { start: null, end: null }
  })
  const firstStep: BookingStep = range.start && range.end ? 2 : 1
  const [step, setStep] = useState<BookingStep>(firstStep)
  const [maxStep, setMaxStep] = useState<BookingStep>(firstStep)
  // 받는 분·연락처는 내 정보로 미리 채운다 (바꿀 수 있다)
  const [form, setForm] = useState(() => ({ ...EMPTY_FORM, name: user?.name ?? '', phone: user?.phone ?? '' }))
  const [availability, setAvailability] = useState<Availability[] | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [agree, setAgree] = useState(false)
  const [err, setErr] = useState('')
  const errRef = useRef<HTMLParagraphElement>(null)

  const carrier = catalog.bySize(size)
  const { start, end } = range
  const n = nights(start, end)

  // 날짜가 정해지면 사이즈별 "그 기간에" 예약 가능한 대수를 받아 온다
  useEffect(() => {
    setAvailability(null)
    if (!start || !end) return
    let alive = true
    api<Availability[]>(`/api/carriers/availability?start=${ymd(start)}&end=${ymd(end)}`)
      .then((a) => alive && setAvailability(a))
      .catch(() => alive && setAvailability(null))
    return () => {
      alive = false
    }
  }, [start, end])

  const goto = (s: BookingStep) => {
    setStep(s)
    setMaxStep((m) => (s > m ? s : m))
    setErr('')
    window.scrollTo(0, 0)
  }

  // prototype validate(): 현재 단계에서 빠진 값이 있으면 메시지를 돌려준다
  const validate = () => {
    if (step === 1 && !(start && end)) return '출발일과 도착일을 모두 선택해주세요.'
    if (step === 2 && availability?.find((x) => x.size === size)?.available === 0)
      return '선택한 기간에 예약할 수 있는 캐리어가 없습니다. 다른 사이즈를 선택해주세요.'
    if (step === 3 && (!form.name.trim() || !form.phone.trim() || !form.addr.trim()))
      return '받는 분, 휴대폰 번호, 주소를 입력해주세요.'
    if (step === 4 && !agree) return '프로토타입 예약임을 확인해주세요.'
    return ''
  }

  const showErr = (e: string) => {
    setErr(e)
    // 모바일에서는 하단 바를 누르므로 메시지가 화면 밖일 수 있다
    if (window.innerWidth <= 720) errRef.current?.scrollIntoView({ block: 'center', behavior: 'smooth' })
  }

  const next = async () => {
    const e = validate()
    if (e) return showErr(e)
    if (step < 4) return goto((step + 1) as BookingStep)
    if (!carrier || !start || !end || submitting) return

    setSubmitting(true)
    try {
      const booking = await api<BookingResult>('/api/bookings', {
        method: 'POST',
        body: {
          size,
          // Product 에서 보던 캐리어를 우선 배정해 달라는 뜻. 사이즈를 바꿨으면 보내지 않는다
          carrierCode: from.size === size ? from.carrierCode : undefined,
          startDate: ymd(start),
          endDate: ymd(end),
          recipientName: form.name,
          phone: form.phone,
          address: form.addr,
          addressDetail: form.addr2,
          requestMessage: form.memo,
          // 화면에 보여준 금액. 서버 계산과 다르면 예약하지 않고 알려준다
          expectedTotal: priceFor(carrier, n),
        },
      })
      navigate('/booking/complete', { replace: true, state: booking })
    } catch (x) {
      const ae = x instanceof ApiError ? x : null
      if (ae?.status === 401) return navigate('/login', { state: { from: { pathname: '/booking' } } })
      // 입력 검증 오류는 첫 번째 필드 메시지를 보여준다
      showErr(Object.values(ae?.fields ?? {})[0] ?? ae?.message ?? '예약을 저장하지 못했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  const prev = () => goto((step - 1) as BookingStep)
  const nextLabel = submitting ? '예약 요청 중…' : NEXT_LABEL[step - 1]

  if (!carrier) {
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
          <p className="small">예약 정보를 불러오는 중입니다.</p>
        )}
      </div>
    )
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
            {step === 1 && (
              <DateStep
                range={range}
                onChange={(r) => {
                  setRange(r)
                  setErr('')
                }}
              />
            )}
            {step === 2 && catalog.list && (
              <CarrierStep carriers={catalog.list} size={size} range={range} availability={availability} onPick={setSize} />
            )}
            {step === 3 && <DeliveryStep form={form} onChange={setForm} range={range} />}
            {step === 4 && start && end && (
              <ConfirmStep
                carrier={carrier}
                start={start}
                end={end}
                form={form}
                agree={agree}
                onAgree={(v) => {
                  setAgree(v)
                  setErr('')
                }}
              />
            )}
            <p className="err" ref={errRef} role="alert">
              {err}
            </p>

            <div className="bk-nav">
              {step > 1 ? (
                <button className="link" onClick={prev}>
                  이전 단계
                </button>
              ) : (
                <span />
              )}
              <Button arrow onClick={next} disabled={submitting}>
                {nextLabel}
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
          <Button variant="secondary" small onClick={prev} aria-label="이전 단계">
            ←
          </Button>
        )}
        <Button small onClick={next} disabled={submitting}>
          {nextLabel}
        </Button>
      </div>
    </>
  )
}
