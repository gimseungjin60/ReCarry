import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import Button from '@/components/Button'
import { INSPECTION_CHECKS } from '@/data/carriers'
import { BOOKING_OCCUPYING, BOOKING_STATUS, CARRIER_EVENT, CARRIER_OPERATIONAL, CARRIER_STATUS } from '@/data/status'
import type {
  ApiCarrier,
  ApiCarrierModel,
  Booking,
  CarrierEventType,
  CarrierSize,
  CarrierStatus,
  Grade,
  InspectionItem,
} from '@/data/types'
import { api, ApiError } from '@/lib/api'
import { invalidateCatalog } from '@/lib/catalog'
import { fmt, parseYmd, ymd } from '@/lib/format'

/* 관리자 캐리어 관리 — 등록 · 상세(운영 상태 · 기본 정보 · 검수 결과 · Story 기록) · 사이즈 상품 수정.
   운영 정책(상태 전이 규칙, 세척·검수 버퍼 등)은 여기서 정하지 않는다. 검증은 backend 가 최종 판단한다. */

const SIZES: CarrierSize[] = ['20', '24', '28']
const EVENT_TYPES = Object.keys(CARRIER_EVENT) as CarrierEventType[]

/** 입력 검증 오류는 첫 번째 필드 메시지, 아니면 서버 메시지 */
const errorText = (e: unknown) =>
  e instanceof ApiError ? (Object.values(e.fields)[0] ?? e.message) : '요청을 처리하지 못했습니다.'

// ---------------------------------------------------------------- 등록 (Admin 목록 위)

export function CarrierCreate() {
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ size: '24' as CarrierSize, code: '', grade: 'A' as Grade, collectedFrom: '', repairSummary: '' })
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setErr('')
    try {
      const c = await api<ApiCarrier>('/api/admin/carriers', { method: 'POST', body: form })
      navigate(`/admin/carriers/${c.code}`)
    } catch (x) {
      setErr(errorText(x))
    } finally {
      setBusy(false)
    }
  }

  if (!open) {
    return (
      <Button small variant="secondary" onClick={() => setOpen(true)} style={{ marginTop: 16 }}>
        캐리어 등록
      </Button>
    )
  }

  return (
    <form className="adm-form" onSubmit={submit} noValidate>
      <p className="small">새 캐리어는 세척·검수 중 상태로 등록됩니다. Carrier ID 와 사이즈는 등록 후 바꿀 수 없습니다.</p>
      <div className="row2">
        <label className="field">
          <span className="l">사이즈</span>
          <select value={form.size} onChange={(e) => setForm({ ...form, size: e.target.value as CarrierSize })}>
            {SIZES.map((s) => (
              <option key={s} value={s}>
                {s}-inch
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span className="l">Carrier ID (예: RC-24-0201)</span>
          <input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })} />
        </label>
      </div>
      <CarrierInfoFields value={form} onChange={(v) => setForm({ ...form, ...v })} />
      <p className="err" role="alert">
        {err}
      </p>
      <div className="adm-actions">
        <Button small type="submit" disabled={busy}>
          {busy ? '등록 중…' : '등록'}
        </Button>
        <Button small variant="secondary" type="button" onClick={() => setOpen(false)}>
          닫기
        </Button>
      </div>
    </form>
  )
}

interface Info {
  grade: Grade
  collectedFrom: string
  repairSummary: string
}

/** 등록 · 수정이 같이 쓰는 기본 정보 입력 */
function CarrierInfoFields({ value, onChange }: { value: Info; onChange: (v: Partial<Info>) => void }) {
  return (
    <>
      <div className="row2">
        <label className="field">
          <span className="l">등급</span>
          <select value={value.grade} onChange={(e) => onChange({ grade: e.target.value as Grade })}>
            <option value="A">A GRADE</option>
            <option value="B">B GRADE</option>
          </select>
        </label>
        <label className="field">
          <span className="l">회수처 (범주)</span>
          <input maxLength={60} value={value.collectedFrom} onChange={(e) => onChange({ collectedFrom: e.target.value })} />
        </label>
      </div>
      <label className="field">
        <span className="l">수리 요약</span>
        <input maxLength={120} value={value.repairSummary} onChange={(e) => onChange({ repairSummary: e.target.value })} />
      </label>
    </>
  )
}

// ---------------------------------------------------------------- 상세

export function AdminCarrier() {
  const { code = '' } = useParams()
  const [c, setC] = useState<ApiCarrier | null>(null)
  const [bookings, setBookings] = useState<Booking[]>([])
  const [err, setErr] = useState('')
  const [msg, setMsg] = useState('')

  const load = useCallback(() => {
    api<ApiCarrier>(`/api/carriers/${encodeURIComponent(code)}`)
      .then(setC)
      .catch((e) => setErr(errorText(e)))
    // 이 캐리어를 점유 중인 예약 — 운영 상태를 바꾸기 전에 확인한다
    api<Booking[]>('/api/admin/bookings')
      .then((list) => setBookings(list.filter((b) => b.carrierCode === code && BOOKING_OCCUPYING.includes(b.status))))
      .catch((e) => setErr(errorText(e)))
  }, [code])
  useEffect(load, [load])

  /** 변경 요청 → 응답(갱신된 캐리어)으로 화면을 바꾼다 */
  const run = async (label: string, req: () => Promise<ApiCarrier>) => {
    setErr('')
    setMsg('')
    try {
      setC(await req())
      setMsg(label)
      invalidateCatalog()
      return true
    } catch (x) {
      setErr(errorText(x))
      return false
    }
  }

  return (
    <div className="wrap acct-pg">
      <div className="crumb" style={{ padding: 0 }}>
        <Link to="/admin">Admin</Link>
        <span>/</span>
        <span style={{ color: 'var(--ink)' }}>캐리어</span>
      </div>
      {c && (
        <>
          <p className="label muted" style={{ marginTop: 24 }}>
            Carrier ID · {c.size}-inch
          </p>
          <h1 className="d-m mono" style={{ marginTop: 12 }}>
            {c.code}
          </h1>
          <p className="small" style={{ marginTop: 8 }}>
            오늘 상태 {CARRIER_STATUS[c.status]}
            {c.featured && ' · 사이즈 대표 캐리어'}
          </p>
        </>
      )}
      <p className="err" role="alert">
        {err}
      </p>
      <p className="small" role="status" style={{ color: 'var(--forest)' }}>
        {msg}
      </p>

      {c && (
        <>
          <section className="adm-sec" aria-labelledby="c-status">
            <h2 className="t-title" id="c-status">
              운영 상태
            </h2>
            <p className="small">운영 상태를 바꿔도 이미 접수된 예약은 자동으로 바뀌지 않습니다.</p>
            <select
              aria-label="운영 상태"
              style={{ marginTop: 12 }}
              value={CARRIER_OPERATIONAL.includes(c.status) ? c.status : 'AVAILABLE'}
              onChange={(e) =>
                run('운영 상태를 바꿨습니다.', () =>
                  api(`/api/admin/carriers/${c.id}/status`, { method: 'PATCH', body: { status: e.target.value as CarrierStatus } }),
                )
              }
            >
              {CARRIER_OPERATIONAL.map((s) => (
                <option key={s} value={s}>
                  {CARRIER_STATUS[s]}
                </option>
              ))}
            </select>
            {bookings.length > 0 && (
              <ul className="adm-list">
                {bookings.map((b) => (
                  <li key={b.bookingNumber}>
                    <Link className="link mono" to={`/admin/bookings/${b.bookingNumber}`}>
                      {b.bookingNumber}
                    </Link>
                    <span>
                      {fmt(parseYmd(b.startDate))} — {fmt(parseYmd(b.endDate))}
                    </span>
                    <span>{BOOKING_STATUS[b.status]}</span>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <InfoSection key={`info-${c.id}`} carrier={c} run={run} />
          <InspectionSection key={`insp-${c.id}-${c.inspectedAt}`} carrier={c} run={run} />
          <EventsSection carrier={c} run={run} />
        </>
      )}
    </div>
  )
}

type Run = (label: string, req: () => Promise<ApiCarrier>) => Promise<boolean>

function InfoSection({ carrier, run }: { carrier: ApiCarrier; run: Run }) {
  const [info, setInfo] = useState<Info>({
    grade: carrier.grade,
    collectedFrom: carrier.collectedFrom,
    repairSummary: carrier.repairSummary,
  })
  const [featured, setFeatured] = useState(carrier.featured)

  const submit = (e: FormEvent) => {
    e.preventDefault()
    run('기본 정보를 저장했습니다.', () =>
      api(`/api/admin/carriers/${carrier.id}`, { method: 'PATCH', body: { ...info, featured } }),
    )
  }

  return (
    <section className="adm-sec" aria-labelledby="c-info">
      <h2 className="t-title" id="c-info">
        기본 정보
      </h2>
      <form className="adm-form" onSubmit={submit} noValidate>
        <CarrierInfoFields value={info} onChange={(v) => setInfo({ ...info, ...v })} />
        <label className="agree" style={{ marginTop: 0 }}>
          <input type="checkbox" checked={featured} onChange={(e) => setFeatured(e.target.checked)} />
          <span>사이즈 대표 캐리어로 상품 상세에 보여줍니다 (기존 대표는 해제됩니다).</span>
        </label>
        <div className="adm-actions">
          <Button small type="submit">
            저장
          </Button>
        </div>
      </form>
    </section>
  )
}

type Result = '' | 'pass' | 'fail'

function InspectionSection({ carrier, run }: { carrier: ApiCarrier; run: Run }) {
  const today = ymd(new Date())
  const [date, setDate] = useState(carrier.inspectedAt ?? today)
  // 기록이 없으면 결과를 비워 둔다 — 통과로 미리 채우지 않는다
  const [rows, setRows] = useState(() =>
    INSPECTION_CHECKS.map(({ item }) => {
      const prev = carrier.inspection.find((x) => x.item === item)
      return { item, result: (prev ? (prev.passed ? 'pass' : 'fail') : '') as Result, note: prev?.note ?? '' }
    }),
  )
  const [err, setErr] = useState('')

  const set = (item: InspectionItem, v: Partial<{ result: Result; note: string }>) =>
    setRows(rows.map((r) => (r.item === item ? { ...r, ...v } : r)))

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (rows.some((r) => !r.result)) return setErr('모든 항목의 결과를 선택해주세요.')
    setErr('')
    run('검수 결과를 저장했습니다.', () =>
      api(`/api/admin/carriers/${carrier.id}/inspection`, {
        method: 'POST',
        body: { inspectedAt: date, checks: rows.map((r) => ({ item: r.item, passed: r.result === 'pass', note: r.note })) },
      }),
    )
  }

  return (
    <section className="adm-sec" aria-labelledby="c-insp">
      <h2 className="t-title" id="c-insp">
        검수 결과
      </h2>
      <p className="small">
        {carrier.inspection.length
          ? `마지막 검수 ${carrier.inspectedAt ?? '—'} · 통과 ${carrier.inspection.filter((x) => x.passed).length}/${carrier.inspection.length}`
          : '아직 항목별 검수 기록이 없습니다. 상품 상세에는 "기록 없음"으로 표시됩니다.'}{' '}
        저장하면 이전 결과를 대체합니다. 운영 상태는 따로 바꿔주세요.
      </p>
      <form className="adm-form" onSubmit={submit} noValidate>
        <label className="field" style={{ maxWidth: 240 }}>
          <span className="l">검수일</span>
          <input type="date" max={today} value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
        <div className="tbl-wrap">
          <table className="tbl" style={{ minWidth: 560 }}>
            <thead>
              <tr>
                <th>항목</th>
                <th>결과</th>
                <th>메모</th>
              </tr>
            </thead>
            <tbody>
              {INSPECTION_CHECKS.map(({ item, label }) => {
                const r = rows.find((x) => x.item === item)!
                return (
                  <tr key={item}>
                    <td>{label}</td>
                    <td>
                      <select
                        aria-label={`${label} 결과`}
                        value={r.result}
                        onChange={(e) => set(item, { result: e.target.value as Result })}
                      >
                        <option value="" disabled>
                          선택
                        </option>
                        <option value="pass">통과</option>
                        <option value="fail">재점검</option>
                      </select>
                    </td>
                    <td style={{ width: '60%' }}>
                      <input
                        className="adm-input"
                        aria-label={`${label} 메모`}
                        maxLength={120}
                        value={r.note}
                        onChange={(e) => set(item, { note: e.target.value })}
                      />
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
        <p className="err" role="alert">
          {err}
        </p>
        <div className="adm-actions">
          <Button small type="submit">
            검수 결과 저장
          </Button>
        </div>
      </form>
    </section>
  )
}

function EventsSection({ carrier, run }: { carrier: ApiCarrier; run: Run }) {
  const empty = { date: ymd(new Date()), type: 'REPAIR' as CarrierEventType, title: '', detail: '' }
  const [form, setForm] = useState(empty)

  const add = async (e: FormEvent) => {
    e.preventDefault()
    const ok = await run('기록을 추가했습니다.', () =>
      api(`/api/admin/carriers/${carrier.id}/events`, { method: 'POST', body: form }),
    )
    if (ok) setForm(empty)
  }

  const remove = (id: number) =>
    run('기록을 삭제했습니다.', () => api(`/api/admin/carriers/${carrier.id}/events/${id}`, { method: 'DELETE' }))

  return (
    <section className="adm-sec" aria-labelledby="c-events">
      <h2 className="t-title" id="c-events">
        Story 기록
      </h2>
      <p className="small">회수 · 수리 · 세척 · 검수 · 여행 이력. 대표 캐리어의 기록은 상품 상세의 Carrier Story 에 보입니다.</p>
      {carrier.events.length > 0 && (
        <ul className="adm-list">
          {carrier.events.map((ev) => (
            <li key={ev.id}>
              <span className="mono">{ev.date}</span>
              <span>{CARRIER_EVENT[ev.type]}</span>
              <span style={{ flex: 1 }}>
                {ev.title}
                {ev.detail && <span className="small"> · {ev.detail}</span>}
              </span>
              <button type="button" className="link small" onClick={() => remove(ev.id)}>
                삭제
              </button>
            </li>
          ))}
        </ul>
      )}
      <form className="adm-form" onSubmit={add} noValidate>
        <div className="row2">
          <label className="field">
            <span className="l">날짜</span>
            <input type="date" value={form.date} onChange={(e) => setForm({ ...form, date: e.target.value })} />
          </label>
          <label className="field">
            <span className="l">종류</span>
            <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as CarrierEventType })}>
              {EVENT_TYPES.map((t) => (
                <option key={t} value={t}>
                  {CARRIER_EVENT[t]}
                </option>
              ))}
            </select>
          </label>
        </div>
        <label className="field">
          <span className="l">제목</span>
          <input maxLength={60} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
        </label>
        <label className="field">
          <span className="l">상세</span>
          <input maxLength={160} value={form.detail} onChange={(e) => setForm({ ...form, detail: e.target.value })} />
        </label>
        <div className="adm-actions">
          <Button small type="submit">
            기록 추가
          </Button>
        </div>
      </form>
    </section>
  )
}

// ---------------------------------------------------------------- 사이즈 상품 (Admin 목록 아래)

export function ModelEditor() {
  const [models, setModels] = useState<ApiCarrierModel[] | null>(null)
  const [editing, setEditing] = useState<CarrierSize | null>(null)
  const [err, setErr] = useState('')

  // 공개 카탈로그를 그대로 읽는다 (화면 캐시가 아니라 매번 서버 값)
  const load = useCallback(() => api<ApiCarrierModel[]>('/api/carriers').then(setModels).catch((e) => setErr(errorText(e))), [])
  useEffect(() => {
    load()
  }, [load])

  return (
    <section className="adm-sec" aria-labelledby="adm-models">
      <h2 className="t-title" id="adm-models">
        사이즈 상품
      </h2>
      <p className="small">가격·문구를 바꿔도 이미 접수된 예약은 예약 시점 금액을 유지합니다.</p>
      <p className="err" role="alert">
        {err}
      </p>
      <ul className="adm-list">
        {models?.map((m) => (
          <li key={m.size} style={{ flexWrap: 'wrap' }}>
            <span className="mono">{m.size}"</span>
            <span style={{ flex: 1 }}>
              {m.name} · {m.price.toLocaleString('ko-KR')}원 / 1박 추가 {m.extraNightPrice.toLocaleString('ko-KR')}원
            </span>
            <button type="button" className="link small" onClick={() => setEditing(editing === m.size ? null : m.size)}>
              {editing === m.size ? '닫기' : '수정'}
            </button>
            {editing === m.size && (
              <ModelForm
                model={m}
                onSaved={() => {
                  setEditing(null)
                  invalidateCatalog()
                  load()
                }}
              />
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}

const MODEL_TEXT = [
  ['name', '상품명', 50],
  ['inch', '인치 표기', 20],
  ['capacity', '용량', 20],
  ['usage', '용도', 60],
  ['dims', '크기', 40],
  ['weight', '무게', 20],
] as const

function ModelForm({ model, onSaved }: { model: ApiCarrierModel; onSaved: () => void }) {
  const [form, setForm] = useState({
    name: model.name,
    inch: model.inch,
    capacity: model.capacity,
    usage: model.usage,
    dims: model.dims,
    weight: model.weight,
    price: String(model.price),
    extraNightPrice: String(model.extraNightPrice),
    headline: model.headline.join('\n'),
    description: model.description,
  })
  const [err, setErr] = useState('')

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setErr('')
    try {
      await api(`/api/admin/carrier-models/${model.size}`, {
        method: 'PATCH',
        body: { ...form, price: Number(form.price), extraNightPrice: Number(form.extraNightPrice) },
      })
      onSaved()
    } catch (x) {
      setErr(errorText(x))
    }
  }

  return (
    <form className="adm-form" style={{ width: '100%' }} onSubmit={submit} noValidate>
      <div className="row2">
        {MODEL_TEXT.map(([k, label, max]) => (
          <label className="field" key={k}>
            <span className="l">{label}</span>
            <input maxLength={max} value={form[k]} onChange={(e) => setForm({ ...form, [k]: e.target.value })} />
          </label>
        ))}
        <label className="field">
          <span className="l">기본 요금 (2박 3일, 원)</span>
          <input type="number" min={0} value={form.price} onChange={(e) => setForm({ ...form, price: e.target.value })} />
        </label>
        <label className="field">
          <span className="l">1박 추가 요금 (원)</span>
          <input
            type="number"
            min={0}
            value={form.extraNightPrice}
            onChange={(e) => setForm({ ...form, extraNightPrice: e.target.value })}
          />
        </label>
      </div>
      <label className="field">
        <span className="l">헤드라인 (줄바꿈 가능)</span>
        <textarea maxLength={120} value={form.headline} onChange={(e) => setForm({ ...form, headline: e.target.value })} />
      </label>
      <label className="field">
        <span className="l">설명</span>
        <textarea maxLength={2000} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
      </label>
      <p className="err" role="alert">
        {err}
      </p>
      <div className="adm-actions">
        <Button small type="submit">
          저장
        </Button>
      </div>
    </form>
  )
}
