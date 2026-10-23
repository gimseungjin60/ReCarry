import { useState } from 'react'
import type { FormEvent, InputHTMLAttributes, ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import Button from '@/components/Button'
import { api, ApiError } from '@/lib/api'
import { useTitle } from '@/lib/useTitle'

/* 비밀번호 찾기 — 이메일로 받은 6자리 코드로 재설정 · 이름 + 휴대폰 번호로 가입 이메일 찾기.
   가입 여부는 화면에 드러내지 않는다 (코드 요청은 항상 "보냈습니다"). */

type Tab = 'reset' | 'email'

const errorText = (e: unknown) =>
  e instanceof ApiError ? (Object.values(e.fields)[0] ?? e.message) : '요청을 처리하지 못했습니다.'

function Field({ label, ...input }: { label: string } & InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="field">
      <span className="l">{label}</span>
      <input {...input} />
    </label>
  )
}

/** 제출 중 상태와 오류를 한 곳에서 다룬다 */
function useSubmit() {
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  const run = (fn: () => Promise<void>) => async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setErr('')
    try {
      await fn()
    } catch (x) {
      setErr(errorText(x))
    } finally {
      setBusy(false)
    }
  }
  return { busy, err, run }
}

function Form({ onSubmit, busy, err, label, children }: { onSubmit: (e: FormEvent) => void; busy: boolean; err: string; label: string; children: ReactNode }) {
  return (
    <form onSubmit={onSubmit} noValidate>
      {children}
      <p className="err" role="alert">
        {err}
      </p>
      <Button block arrow type="submit" disabled={busy}>
        {busy ? '처리 중…' : label}
      </Button>
    </form>
  )
}

function ResetByEmail() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [code, setCode] = useState('')
  const [pw, setPw] = useState('')
  const { busy, err, run } = useSubmit()

  if (!sent) {
    return (
      <Form
        label="인증 코드 받기"
        busy={busy}
        err={err}
        onSubmit={run(async () => {
          await api('/api/auth/password-reset/request', { method: 'POST', body: { email } })
          setSent(true)
        })}
      >
        <Field label="가입한 이메일" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
      </Form>
    )
  }

  return (
    <Form
      label="비밀번호 재설정"
      busy={busy}
      err={err}
      onSubmit={run(async () => {
        await api('/api/auth/password-reset/confirm', { method: 'POST', body: { email, code, newPassword: pw } })
        navigate('/login', { replace: true, state: { notice: '비밀번호를 바꿨습니다. 새 비밀번호로 로그인해주세요.' } })
      })}
    >
      <p className="small" role="status">
        {email} 로 가입된 계정이 있으면 인증 코드(6자리)를 보냈습니다. 15분 안에 입력해주세요. 메일이 없다면 스팸함을 확인하거나{' '}
        <button type="button" className="link" onClick={() => setSent(false)}>
          다시 요청
        </button>
        하세요.
      </p>
      <Field label="인증 코드" inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={code} onChange={(e) => setCode(e.target.value.trim())} />
      <Field
        label="새 비밀번호 (영문·숫자 포함 8자 이상)"
        type="password"
        autoComplete="new-password"
        value={pw}
        onChange={(e) => setPw(e.target.value)}
      />
    </Form>
  )
}

function FindEmail() {
  const [form, setForm] = useState({ name: '', phone: '' })
  const [found, setFound] = useState<string[] | null>(null)
  const { busy, err, run } = useSubmit()

  return (
    <Form
      label="이메일 찾기"
      busy={busy}
      err={err}
      onSubmit={run(async () => setFound((await api<{ emails: string[] }>('/api/auth/find-email', { method: 'POST', body: form })).emails))}
    >
      <Field label="이름" autoComplete="name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
      <Field
        label="휴대폰 번호"
        inputMode="tel"
        autoComplete="tel"
        placeholder="010-0000-0000"
        value={form.phone}
        onChange={(e) => setForm({ ...form, phone: e.target.value })}
      />
      {found && (
        <div className="note" role="status" style={{ marginBottom: 16 }}>
          {found.length ? (
            <span>
              가입한 이메일: <b className="mono">{found.join(', ')}</b> — 일부는 가려서 보여드립니다.
            </span>
          ) : (
            <span>일치하는 계정이 없습니다. 가입할 때 입력한 이름과 휴대폰 번호를 확인해주세요.</span>
          )}
        </div>
      )}
    </Form>
  )
}

export default function Forgot() {
  useTitle('비밀번호 찾기')
  const [tab, setTab] = useState<Tab>('reset')
  return (
    <div className="wrap auth-pg">
      <p className="label muted">Account</p>
      <h1 className="d-m" style={{ marginTop: 12 }}>
        비밀번호 찾기
      </h1>
      <nav className="legal-nav" aria-label="찾기 방법" style={{ marginTop: 32 }}>
        <button type="button" aria-current={tab === 'reset' ? 'page' : undefined} onClick={() => setTab('reset')}>
          이메일로 비밀번호 재설정
        </button>
        <button type="button" aria-current={tab === 'email' ? 'page' : undefined} onClick={() => setTab('email')}>
          휴대폰 번호로 이메일 찾기
        </button>
      </nav>
      {tab === 'reset' ? <ResetByEmail /> : <FindEmail />}
      <p className="small" style={{ marginTop: 24 }}>
        <Link className="link" to="/login">
          로그인으로 돌아가기
        </Link>
      </p>
    </div>
  )
}
