import { useState } from 'react'
import type { FormEvent, InputHTMLAttributes, ReactNode } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import Button from '@/components/Button'
import { ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'

/* 로그인 / 회원가입. 같은 레이아웃이라 한 파일에 둔다.
   RequireAuth 가 넘긴 from 이 있으면 로그인 후 그 화면(과 state)으로 돌아간다. */

interface From {
  pathname: string
  state?: unknown
}

/** 로그인되면 돌아갈 곳. 리다이렉트는 이 한 곳에서만 한다 (두 군데서 navigate 하면 경합한다) */
function ReturnTo() {
  const from = (useLocation().state as { from?: From } | null)?.from
  return <Navigate to={from?.pathname ?? '/mypage'} state={from?.state} replace />
}

function Field({ label, error, ...input }: { label: string; error?: string } & InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="field">
      <span className="l">{label}</span>
      <input {...input} aria-invalid={!!error} />
      {error && <span className="err" style={{ display: 'block', marginTop: 6 }}>{error}</span>}
    </label>
  )
}

/** 필수 동의 체크 + 내용 펼쳐 보기 */
function Consent({
  checked,
  onChange,
  label,
  error,
  children,
}: {
  checked: boolean
  onChange: (v: boolean) => void
  label: string
  error?: string
  children: ReactNode
}) {
  return (
    <div className="consent">
      <label className="agree">
        <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} aria-invalid={!!error} />
        <span>{label}</span>
      </label>
      <details>
        <summary className="small">내용 보기</summary>
        <p className="small">{children}</p>
      </details>
      {error && <span className="err" style={{ display: 'block', marginTop: 6 }}>{error}</span>}
    </div>
  )
}

export function Login() {
  const { user, login } = useAuth()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)

  if (user) return <ReturnTo />

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setErr('')
    try {
      await login(email, password)
    } catch (x) {
      setErr(x instanceof ApiError ? x.message : '로그인하지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="wrap auth-pg">
      <p className="label muted">Account</p>
      <h1 className="d-m" style={{ marginTop: 12 }}>
        로그인
      </h1>
      <form onSubmit={submit} noValidate>
        <Field label="이메일" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <Field
          label="비밀번호"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        <p className="err" role="alert">
          {err}
        </p>
        <Button block arrow type="submit" disabled={busy}>
          {busy ? '로그인 중…' : '로그인'}
        </Button>
      </form>
      <p className="small" style={{ marginTop: 24 }}>
        처음이신가요?{' '}
        <Link className="link" to="/signup" state={location.state}>
          회원가입
        </Link>
      </p>
    </div>
  )
}

export function Signup() {
  const { user, signup } = useAuth()
  const location = useLocation()
  const [form, setForm] = useState({ email: '', password: '', name: '', phone: '' })
  const [agree, setAgree] = useState({ agreeTerms: false, agreePrivacy: false })
  const [fields, setFields] = useState<Record<string, string>>({})
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)

  if (user) return <ReturnTo />

  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value })

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setErr('')
    setFields({})
    try {
      await signup({ ...form, ...agree })
    } catch (x) {
      if (x instanceof ApiError) {
        setFields(x.fields)
        setErr(Object.keys(x.fields).length ? '' : x.message)
      } else setErr('가입하지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="wrap auth-pg">
      <p className="label muted">Account</p>
      <h1 className="d-m" style={{ marginTop: 12 }}>
        회원가입
      </h1>
      <form onSubmit={submit} noValidate>
        <Field label="이메일" type="email" autoComplete="email" value={form.email} onChange={set('email')} error={fields.email} />
        <Field
          label="비밀번호 (영문·숫자 포함 8자 이상)"
          type="password"
          autoComplete="new-password"
          value={form.password}
          onChange={set('password')}
          error={fields.password}
        />
        <div className="row2">
          <Field label="이름" autoComplete="name" value={form.name} onChange={set('name')} error={fields.name} />
          <Field
            label="휴대폰 번호"
            inputMode="tel"
            autoComplete="tel"
            placeholder="010-0000-0000"
            value={form.phone}
            onChange={set('phone')}
            error={fields.phone}
          />
        </div>
        <Consent
          checked={agree.agreeTerms}
          onChange={(v) => setAgree({ ...agree, agreeTerms: v })}
          label="[필수] 이용약관에 동의합니다."
          error={fields.agreeTerms}
        >
          RECARRY 는 Web MVP 단계로, 이용약관 전문은 정식 서비스 전에 확정해 안내합니다. 현재 예약은 예약 요청만 저장되며 결제·배송은
          발생하지 않습니다.
        </Consent>
        <Consent
          checked={agree.agreePrivacy}
          onChange={(v) => setAgree({ ...agree, agreePrivacy: v })}
          label="[필수] 개인정보 수집·이용에 동의합니다."
          error={fields.agreePrivacy}
        >
          수집 항목: 이메일, 비밀번호(암호화 저장), 이름, 휴대폰 번호 · 예약 시 받는 분, 연락처, 배송 주소, 요청사항
          <br />
          이용 목적: 회원 식별, 로그인, 예약 요청 접수와 안내
          <br />
          보유 기간 등 개인정보 처리방침 전문은 정식 서비스 전에 확정해 안내합니다.
        </Consent>
        <p className="err" role="alert">
          {err}
        </p>
        <Button block arrow type="submit" disabled={busy}>
          {busy ? '가입 중…' : '가입하기'}
        </Button>
      </form>
      <p className="small" style={{ marginTop: 24 }}>
        이미 계정이 있나요?{' '}
        <Link className="link" to="/login" state={location.state}>
          로그인
        </Link>
      </p>
    </div>
  )
}
