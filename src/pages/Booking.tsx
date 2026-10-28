import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import Button from '@/components/Button'
import VerifyEmailNotice from '@/components/VerifyEmailNotice'
import BookingStepper from '@/components/BookingStepper'
import BookingSummary from '@/components/BookingSummary'
import type { DateRange } from '@/components/Calendar'
import DateStep from '@/booking/steps/DateStep'
import CarrierStep from '@/booking/steps/CarrierStep'
import DeliveryStep, { EMPTY_FORM } from '@/booking/steps/DeliveryStep'
import type { DeliveryForm } from '@/booking/steps/DeliveryStep'
import CheckoutLogin from '@/booking/CheckoutLogin'
import ConfirmStep from '@/booking/steps/ConfirmStep'
import { DEFAULT_SIZE, isCarrierSize } from '@/data/carriers'
import type { BookingStep } from '@/data/process'
import type { Availability, Booking as BookingResult, CarrierSize } from '@/data/types'
import { isPhone, nights, parseYmd, priceFor, seoulToday, stayLabel, won, ymd } from '@/lib/format'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { invalidateCatalog, useCatalog } from '@/lib/catalog'
import { useTitle } from '@/lib/useTitle'

/* PRD §4 Booking — 쇼핑몰 주문서처럼 로그인 없이 날짜 · 캐리어 · 배송 정보까지 고르고,
   마지막 "예약 요청"에서만 로그인(카카오 · 이메일)한다. 고른 내용은 sessionStorage 에 남아
   로그인 · 가입 · 카카오를 다녀와도 그대로 이어진다.
   홈 첫 화면 · Product 에서 사이즈·날짜(·보던 Carrier ID)를 받아 오면 사이즈는 이미 고른 것이므로
   Step 02(캐리어 선택)를 건너뛰고 Step 03 부터 시작한다. 바꾸고 싶으면 단계 표시에서 Step 02 를 누른다.
   확정하면 POST /api/bookings — 금액·재고·날짜는 서버가 다시 검증한다. */

const DRAFT_KEY = 'recarry.booking'

interface Draft {
  size: string
  carrierCode?: string
  start: string
  end: string
  form: DeliveryForm
  step: BookingStep
  /** 사이즈를 이미 골라 Step 02 를 건너뛰는 중 */
  skipCarrier?: boolean
  agree?: boolean
}

/** 이어서 할 예약 (같은 탭 안에서만). 깨졌거나 지난 날짜면 버린다 */
function readDraft(): Draft | null {
  try {
    const d = JSON.parse(sessionStorage.getItem(DRAFT_KEY) ?? 'null') as Draft | null
    return d && asDate(parseYmd(d.start)) && asDate(parseYmd(d.end)) ? d : null
  } catch {
    return null
  }
}

const NEXT_LABEL = ['캐리어 선택', '배송 정보 입력', '예약 확인', '예약 요청하기']

interface FromProduct {
  size?: string
  carrierCode?: string
  start?: unknown
  end?: unknown
}

/** 넘겨받은 날짜는 아직 유효할 때만 쓴다 (오래된 탭을 복원하면 지난 날짜일 수 있다) */
const asDate = (v: unknown) => (v instanceof Date && v > seoulToday() ? v : null)

export default function Booking() {
  useTitle('예약')
  const navigate = useNavigate()
  const location = useLocation()
  // 상품 · 홈에서 들어왔으면(state) 새 예약 — 이어서 할 예약(draft)과 섞지 않는다.
  // state 없이 들어오면(로그인 · 가입 · 카카오에서 돌아옴, 메뉴) 이어서 할 예약을 쓴다
  const [entry] = useState(() => location.state as FromProduct | null)
  const [draft] = useState(() => (entry ? null : readDraft()))
  const from = (entry ??
    (draft ? { size: draft.size, carrierCode: draft.carrierCode, start: parseYmd(draft.start), end: parseYmd(draft.end) } : {})) as FromProduct
  const { user, options } = useAuth()
  const catalog = useCatalog()

  const [size, setSize] = useState<CarrierSize>(isCarrierSize(from.size) ? from.size : DEFAULT_SIZE)
  const [range, setRange] = useState<DateRange>(() => {
    const start = asDate(from.start)
    const end = asDate(from.end)
    return start && end && end > start ? { start, end } : { start: null, end: null }
  })
  // 사이즈를 들고 왔으면 Step 02 를 건너뛴다 (직접 Step 02 를 열면 그때부터는 건너뛰지 않는다)
  const [skipCarrier, setSkipCarrier] = useState(() => (draft ? !!draft.skipCarrier : isCarrierSize(from.size)))
  const firstStep: BookingStep = draft?.step ?? (range.start && range.end ? (skipCarrier ? 3 : 2) : 1)
  const [step, setStep] = useState<BookingStep>(firstStep)
  const [maxStep, setMaxStep] = useState<BookingStep>(firstStep)
  // 받는 분·연락처는 내 정보로 미리 채운다 (바꿀 수 있다)
  const [form, setForm] = useState(() => draft?.form ?? { ...EMPTY_FORM, name: user?.name ?? '', phone: user?.phone ?? '' })
  // 비회원이 "예약 요청"을 누르면 로그인 방법을 보여준다
  const [gate, setGate] = useState(false)
  const [addrHint, setAddrHint] = useState('')
  const [availability, setAvailability] = useState<Availability[] | null>(null)
  const [availFailed, setAvailFailed] = useState(false)
  const [availTry, setAvailTry] = useState(0)
  const [submitting, setSubmitting] = useState(false)
  // 로그인하고 돌아와도 동의 체크가 풀리지 않게 draft 에 남긴다
  const [agree, setAgree] = useState(() => draft?.agree ?? false)
  const [err, setErr] = useState('')
  const errRef = useRef<HTMLParagraphElement>(null)

  const carrier = catalog.bySize(size)
  const { start, end } = range
  const n = nights(start, end)
  // 고른 사이즈의 그 기간 재고 · 보증금 (서버 계산). 못 받았으면 null
  const mine = availability?.find((x) => x.size === size) ?? null
  const soldOut = mine?.available === 0
  const deposit = mine?.deposit ?? null

  // 들고 온 state 는 한 번만 쓴다 — 새로고침 · 뒤로가기로 다시 들어오면 새 예약이 아니라 draft 로 이어진다
  useEffect(() => {
    if (location.state) navigate(location.pathname, { replace: true, state: null })
  }, [location.state, location.pathname, navigate])

  // 고른 내용을 남겨 둔다 — 로그인 · 가입 · 카카오를 다녀와도 이어서
  useEffect(() => {
    if (!start || !end) return
    const d: Draft = {
      size,
      carrierCode: from.size === size ? from.carrierCode : undefined,
      start: ymd(start),
      end: ymd(end),
      form,
      step,
      skipCarrier,
      agree,
    }
    try {
      sessionStorage.setItem(DRAFT_KEY, JSON.stringify(d))
    } catch {
      /* 저장소를 못 쓰면 이어하기만 안 된다 */
    }
  }, [size, start, end, form, step, skipCarrier, agree, from.size, from.carrierCode])

  // 중간에 로그인했으면 비어 있는 받는 분 · 연락처를 내 정보로
  useEffect(() => {
    if (user) setForm((f) => (f.name || f.phone ? f : { ...f, name: user.name, phone: user.phone ?? '' }))
  }, [user])

  // 배송 정보 단계: 주소가 비어 있으면 최근 예약의 배송지를 불러온다 (쇼핑몰의 "최근 배송지")
  const needAddr = step === 3 && !!user && !form.addr
  useEffect(() => {
    if (!needAddr) return
    let alive = true
    api<BookingResult[]>('/api/bookings')
      .then((list) => {
        const last = list.find((b) => b.address)
        if (!alive || !last) return
        setForm((f) => (f.addr ? f : { ...f, addr: last.address, addr2: last.addressDetail }))
        setAddrHint('최근 예약의 배송지를 불러왔습니다. 다른 곳이면 바꿔주세요.')
      })
      .catch(() => {})
    return () => {
      alive = false
    }
  }, [needAddr])

  // 날짜가 정해지면 사이즈별 "그 기간에" 예약 가능한 대수를 받아 온다
  useEffect(() => {
    setAvailability(null)
    setAvailFailed(false)
    if (!start || !end) return
    let alive = true
    api<Availability[]>(`/api/carriers/availability?start=${ymd(start)}&end=${ymd(end)}`)
      .then((a) => alive && setAvailability(a))
      .catch(() => alive && setAvailFailed(true))
    return () => {
      alive = false
    }
  }, [start, end, availTry])

  const goto = (s: BookingStep) => {
    if (s === 2) setSkipCarrier(false)
    setStep(s)
    setMaxStep((m) => (s > m ? s : m))
    setErr('')
    window.scrollTo(0, 0)
  }

  // Step 02 를 건너뛰었는데 고른 사이즈가 그 기간에 마감이면 캐리어 선택으로 돌려보낸다
  useEffect(() => {
    if (!soldOut || step <= 2) return
    setStep(2)
    setMaxStep(2)
    setSkipCarrier(false)
    window.scrollTo(0, 0)
    showErr('선택한 기간에 이 사이즈는 예약이 마감되었습니다. 다른 사이즈를 선택해주세요.')
  }, [soldOut, step]) // eslint-disable-line react-hooks/exhaustive-deps -- showErr 는 매 렌더 새로 만들어지는 함수

  // prototype validate(): 현재 단계에서 빠진 값이 있으면 메시지를 돌려준다
  const validate = () => {
    if (!(start && end)) return step === 1 ? '출발일과 도착일을 모두 선택해주세요.' : '여행 날짜를 다시 선택해주세요. (Step 01)'
    if (step === 2 && soldOut)
      return '선택한 기간에 예약할 수 있는 캐리어가 없습니다. 다른 사이즈를 선택해주세요.'
    if (step === 3 && (!form.name.trim() || !form.phone.trim() || !form.addr.trim()))
      return '받는 분, 휴대폰 번호, 주소를 입력해주세요.'
    if (step === 3 && !isPhone(form.phone)) return '휴대폰 번호 형식을 확인해주세요. (예: 010-1234-5678)'
    if (step === 4 && !agree) return '예약 내용과 대여 정책을 확인해주세요.'
    if (step === 4 && user?.needsEmailVerification) return '이메일 인증을 마친 뒤 예약할 수 있습니다. 위 안내에서 인증 메일을 다시 받아주세요.'
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
    // 사이즈를 이미 골랐으면 날짜 다음은 바로 배송 정보 (마감이면 캐리어 선택으로)
    if (step < 4) return goto((step === 1 && skipCarrier && !soldOut ? 3 : step + 1) as BookingStep)
    if (!carrier || !start || !end || submitting) return
    // 주문서 마지막 단계에서만 로그인 — 고른 내용은 draft 로 남아 있다
    if (!user) return setGate(true)
    if (user.consentRequired) return navigate('/consent', { state: { from: { pathname: '/booking' } } })

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
      sessionStorage.removeItem(DRAFT_KEY)
      navigate('/booking/complete', { replace: true, state: booking })
    } catch (x) {
      const ae = x instanceof ApiError ? x : null
      // 세션이 끝났으면 다시 로그인한 뒤 이어서 — 고른 내용은 draft 에 있다 (state 를 넘기면 새 예약으로 읽힌다)
      if (ae?.status === 401) return navigate('/login', { state: { from: { pathname: '/booking' } } })
      // 요금이 바뀌었으면 카탈로그를 다시 받아 화면 금액을 서버 금액에 맞춘다 (다시 누르면 새 금액으로 요청)
      if (ae?.code === 'PRICE_MISMATCH') {
        invalidateCatalog()
        catalog.retry()
      }
      // 입력 검증 오류는 첫 번째 필드 메시지를 보여준다
      showErr(Object.values(ae?.fields ?? {})[0] ?? ae?.message ?? '예약을 저장하지 못했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  const prev = () => goto((step === 3 && skipCarrier ? 1 : step - 1) as BookingStep)
  // Step 01 달력 아래: 고른 사이즈가 그 기간에 남아 있는지
  const dateNote =
    skipCarrier && start && end
      ? !availability
        ? availFailed
          ? ''
          : '예약 가능 여부를 확인하고 있습니다…'
        : soldOut
          ? `${carrier?.name ?? '이 사이즈'}는 이 기간에 마감입니다. 다음 단계에서 다른 사이즈를 고를 수 있습니다.`
          : mine
            ? `${carrier?.name ?? ''} · 이 기간 ${mine.available}대 예약 가능`
            : ''
      : ''
  const nextLabel = submitting
    ? '예약 요청 중…'
    : step === 4 && !user
      ? '로그인하고 예약 요청'
      : step === 1 && skipCarrier && !soldOut
        ? NEXT_LABEL[1] // 캐리어 선택을 건너뛰므로 다음은 배송 정보
        : NEXT_LABEL[step - 1]

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
          <VerifyEmailNotice />
        </div>

        <div className="grid bk">
          <div className="bk-main">
            {step === 1 && (
              <DateStep
                range={range}
                onChange={(r) => {
                  setRange(r)
                  setAgree(false) // 날짜가 바뀌면 예약 내용 확인을 다시 받는다
                  setErr('')
                }}
                note={dateNote}
              />
            )}
            {step === 2 && catalog.list && (
              <CarrierStep
                carriers={catalog.list}
                size={size}
                range={range}
                availability={availability}
                loading={!!(start && end) && !availability && !availFailed}
                onPick={(s) => {
                  if (s !== size) setAgree(false)
                  setSize(s)
                  setErr('')
                }}
              />
            )}
            {step === 3 && <DeliveryStep form={form} onChange={setForm} range={range} hint={addrHint} />}
            {step === 4 && start && end && (
              <ConfirmStep
                deposit={deposit}
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
            {availFailed && (
              <p className="note" role="alert" style={{ marginTop: 24 }}>
                <span>예약 가능 여부와 보증금을 불러오지 못했습니다.</span>
                <button className="link" onClick={() => setAvailTry((t) => t + 1)}>
                  다시 불러오기
                </button>
              </p>
            )}
            <p className="err" ref={errRef} role="alert">
              {err}
            </p>
            {step === 4 && gate && !user && <CheckoutLogin kakao={options.kakao} />}

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

          <BookingSummary carrier={carrier} start={start} end={end} deposit={deposit} />
        </div>
      </div>

      <div className="bk-bar">
        <div className="tt">
          <b>{n ? won(priceFor(carrier, n) + (deposit ?? 0)) : won(carrier.price) + '~'}</b>
          {n && deposit !== null ? '결제 예정 (보증금 포함)' : '대여 요금'} · {carrier.name}
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
