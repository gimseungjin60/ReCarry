import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import Button from '@/components/Button'
import { Link } from 'react-router-dom'
import { BOOKING_STATUS, CARRIER_EVENT, CARRIER_STATUS } from '@/data/status'
import type { Booking } from '@/data/types'
import { api, ApiError } from '@/lib/api'
import { addDays, fmt, parseYmd, ymd } from '@/lib/format'

/* 관리자 운영 보조 — 날짜별 배송·회수 일정 (예약 날짜에서 계산) · 최근 변경 기록.
   배송·회수 방식은 아직 정하지 않았다 — 여기서는 "이 날 보낼 것 / 받을 것" 목록만 보여준다. */

const errorText = (e: unknown) => (e instanceof ApiError ? e.message : '요청을 처리하지 못했습니다.')

// 배송: 예약 요청·확정 건이 출발 전날 도착. 회수: 확정·이용 중 건의 도착일
const SHIP = ['REQUESTED', 'CONFIRMED']
const PICKUP = ['CONFIRMED', 'IN_USE']

/** version: 같은 화면에서 예약 상태가 바뀌면 올라간다 — 그때 다시 불러온다 */
export function Schedule({ version }: { version: number }) {
  const [day, setDay] = useState(ymd(new Date()))
  const [list, setList] = useState<Booking[] | null>(null)
  const [err, setErr] = useState('')

  useEffect(() => {
    api<Booking[]>('/api/admin/bookings')
      .then(setList)
      .catch((e) => setErr(errorText(e)))
  }, [version])

  const ship = list?.filter((b) => b.deliveryDate === day && SHIP.includes(b.status)) ?? []
  const pickup = list?.filter((b) => b.pickupDate === day && PICKUP.includes(b.status)) ?? []
  const shift = (n: number) => setDay(ymd(addDays(parseYmd(day), n)))

  return (
    <section className="adm-sec" aria-labelledby="adm-schedule">
      <h2 className="t-title" id="adm-schedule">
        배송 · 회수 일정
      </h2>
      <p className="small">출발 전날 도착(배송) · 도착일 회수 기준입니다. 예약 요청 건은 확정 전이라 배송 목록에 "요청"으로 표시됩니다.</p>
      <div className="adm-actions" style={{ alignItems: 'center', marginTop: 12 }}>
        <button type="button" className="link small" onClick={() => shift(-1)}>
          ← 전날
        </button>
        <input type="date" className="adm-input" style={{ width: 'auto' }} value={day} onChange={(e) => e.target.value && setDay(e.target.value)} />
        <button type="button" className="link small" onClick={() => shift(1)}>
          다음날 →
        </button>
      </div>
      <p className="err" role="alert">
        {err}
      </p>
      <div className="stock">
        {(
          [
            ['배송', ship],
            ['회수', pickup],
          ] as const
        ).map(([label, rows]) => (
          <div key={label}>
            <p className="label muted">
              {label} · {fmt(parseYmd(day))}
            </p>
            <p className="n">{rows.length}건</p>
            <ul className="adm-list" style={{ borderTop: 0, marginTop: 0 }}>
              {rows.map((b) => (
                <li key={b.bookingNumber} style={{ flexWrap: 'wrap' }}>
                  <Link className="link mono" to={`/admin/bookings/${b.bookingNumber}`}>
                    {b.bookingNumber}
                  </Link>
                  <span className="mono">{b.carrierCode}</span>
                  <span>{b.recipientName}</span>
                  <span className="small">{BOOKING_STATUS[b.status]}</span>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </section>
  )
}

interface Audit {
  at: string
  admin: string
  action: string
  target: string
  detail: string
  ip: string
}

const ACTION: Record<string, string> = {
  CARRIER_CREATE: '캐리어 등록',
  CARRIER_UPDATE: '캐리어 수정',
  CARRIER_STATUS: '캐리어 상태',
  CARRIER_INSPECTION: '검수 기록',
  CARRIER_EVENT_ADD: 'Story 추가',
  CARRIER_EVENT_DELETE: 'Story 삭제',
  MODEL_UPDATE: '상품 수정',
  BOOKING_STATUS: '예약 상태',
  BOOKING_PAYMENT: '입금 확인',
  BOOKING_REFUND: '환불 송금',
  BOOKING_DEPOSIT: '보증금 정산',
  BOOKING_VIEW: '예약 상세 조회',
  BOOKING_LIST_VIEW: '예약 목록 조회',
  TOTP_ENABLE: '2단계 인증 켬',
  TOTP_DISABLE: '2단계 인증 끔',
}

/** "REPAIR → AVAILABLE" 같은 상태 코드를 화면 문구로 */
const LABELS: Record<string, string> = { ...CARRIER_EVENT, ...CARRIER_STATUS, ...BOOKING_STATUS }
const readable = (detail: string) => detail.replace(/\b[A-Z_]{4,}\b/g, (t) => LABELS[t] ?? t)

export function AuditLog() {
  const [open, setOpen] = useState(false)
  const [kind, setKind] = useState<'CHANGE' | 'VIEW'>('CHANGE')
  const [logs, setLogs] = useState<Audit[] | null>(null)
  const [err, setErr] = useState('')

  useEffect(() => {
    if (!open) return
    setLogs(null)
    api<Audit[]>(`/api/admin/audit-logs?kind=${kind}`)
      .then(setLogs)
      .catch((e) => setErr(errorText(e)))
  }, [open, kind])

  return (
    <section className="adm-sec" aria-labelledby="adm-audit">
      <h2 className="t-title" id="adm-audit">
        변경 기록
      </h2>
      <p className="small">
        관리자가 바꾼 정보와 고객 개인정보(예약 목록 · 상세)를 조회한 기록입니다 (최근 100건). 기록은 지우지 않고 1년 이상
        보관합니다 — 개인정보 접속기록 보관 의무.
      </p>
      <div className="adm-actions" style={{ marginTop: 8, alignItems: 'center' }}>
        <button type="button" className="link small" onClick={() => setOpen(!open)}>
          {open ? '닫기' : '기록 보기'}
        </button>
        {open && (
          <select aria-label="기록 종류" value={kind} onChange={(e) => setKind(e.target.value as 'CHANGE' | 'VIEW')}>
            <option value="CHANGE">변경 기록</option>
            <option value="VIEW">고객정보 조회 기록</option>
          </select>
        )}
      </div>
      <p className="err" role="alert">
        {err}
      </p>
      {open && logs && (
        <div className="tbl-wrap">
          <table className="tbl">
            <thead>
              <tr>
                <th>시각</th>
                <th>관리자</th>
                <th>작업</th>
                <th>대상</th>
                <th>내용</th>
                <th>접속지</th>
              </tr>
            </thead>
            <tbody>
              {logs.map((l, i) => (
                <tr key={i}>
                  <td className="mono">{new Date(l.at).toLocaleString('ko-KR')}</td>
                  <td>{l.admin}</td>
                  <td>{ACTION[l.action] ?? l.action}</td>
                  <td className="mono">{l.target}</td>
                  <td style={{ whiteSpace: 'normal' }}>{readable(l.detail)}</td>
                  <td className="mono">{l.ip || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {logs.length === 0 && <p className="small">기록이 없습니다.</p>}
        </div>
      )}
    </section>
  )
}

/* 관리자 2단계 인증 — Google Authenticator 같은 OTP 앱에 설정 키를 등록한다.
   QR 코드 대신 키를 직접 입력하는 방식 (라이브러리 없이). 휴대폰을 잃어버리면 운영자가 DB 에서 끈다. */
export function TwoFactor() {
  const [enabled, setEnabled] = useState<boolean | null>(null)
  const [setup, setSetup] = useState<{ secret: string; uri: string } | null>(null)
  const [code, setCode] = useState('')
  const [err, setErr] = useState('')
  const [msg, setMsg] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(
    () => api<{ enabled: boolean }>('/api/admin/2fa').then((s) => setEnabled(s.enabled)).catch((e) => setErr(errorText(e))),
    [],
  )
  useEffect(() => {
    load()
  }, [load])

  const act = async (fn: () => Promise<void>, done: string) => {
    if (busy) return
    setBusy(true)
    setErr('')
    setMsg('')
    try {
      await fn()
      setMsg(done)
      setCode('')
    } catch (x) {
      setErr(errorText(x))
    } finally {
      setBusy(false)
    }
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (enabled) {
      if (!window.confirm('2단계 인증을 끌까요? 비밀번호만으로 관리자 로그인이 가능해집니다.')) return
      act(async () => {
        await api('/api/admin/2fa/disable', { method: 'POST', body: { code } })
        setEnabled(false)
      }, '2단계 인증을 껐습니다.')
    } else {
      act(async () => {
        await api('/api/admin/2fa/enable', { method: 'POST', body: { code } })
        setEnabled(true)
        setSetup(null)
      }, '2단계 인증을 켰습니다. 다음 로그인부터 인증 코드가 필요합니다.')
    }
  }

  return (
    <section className="adm-sec" aria-labelledby="adm-2fa">
      <h2 className="t-title" id="adm-2fa">
        2단계 인증
      </h2>
      <p className="small">
        {enabled === null
          ? '불러오는 중…'
          : enabled
            ? '켜져 있습니다. 로그인할 때 OTP 앱의 6자리 코드가 필요합니다.'
            : '꺼져 있습니다. 관리자 계정은 고객 정보를 볼 수 있으므로 켜두기를 권합니다.'}
      </p>
      <p className="err" role="alert">
        {err}
      </p>
      <p className="small" role="status" style={{ color: 'var(--brand)' }}>
        {msg}
      </p>
      {enabled === false && !setup && (
        <Button small onClick={() => act(async () => setSetup(await api('/api/admin/2fa/setup', { method: 'POST' })), '')}>
          설정 시작
        </Button>
      )}
      {setup && (
        <div className="note" style={{ flexDirection: 'column', maxWidth: 560 }}>
          <span>1. OTP 앱(Google Authenticator 등)에서 "설정 키 입력"을 고르고 아래 키를 넣으세요. 계정 이름은 자유입니다.</span>
          <b className="mono" style={{ fontSize: 16, letterSpacing: '.08em' }}>{setup.secret.match(/.{1,4}/g)?.join(' ')}</b>
          <span>
            휴대폰에서 이 화면을 보고 있다면{' '}
            <a className="link" href={setup.uri}>
              OTP 앱으로 바로 열기
            </a>
          </span>
          <span>2. 앱에 나온 6자리 코드를 아래에 입력하면 켜집니다.</span>
        </div>
      )}
      {(setup || enabled) && (
        <form className="adm-form" style={{ maxWidth: 360 }} onSubmit={submit} noValidate>
          <label className="field">
            <span className="l">{enabled ? '끄려면 현재 코드 입력' : '앱의 6자리 코드'}</span>
            <input
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/[^0-9]/g, ''))}
            />
          </label>
          <Button small type="submit" variant={enabled ? 'secondary' : 'primary'} disabled={busy}>
            {enabled ? '2단계 인증 끄기' : '확인하고 켜기'}
          </Button>
        </form>
      )}
    </section>
  )
}
