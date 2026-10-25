import { useEffect, useState } from 'react'
import type { FormEvent, InputHTMLAttributes, ReactNode } from 'react'
import { Link, Navigate, useLocation, useSearchParams } from 'react-router-dom'
import Button from '@/components/Button'
import { api, ApiError, kakaoStartUrl } from '@/lib/api'
import type { AuthResponse } from '@/data/types'
import { useAuth } from '@/lib/auth'
import { useTitle } from '@/lib/useTitle'

/* 로그인 / 회원가입. 같은 레이아웃이라 한 파일에 둔다.
   RequireAuth · 예약 마지막 단계가 넘긴 from 이 있으면 로그인 후 그 화면(과 state)으로 돌아간다.
   카카오: 서버가 OAuth 를 처리한다. 처음 오는 사람은 /signup?via=kakao 로 와서 받은 정보로 채운 가입서를 마친다. */

const KAKAO_ERROR: Record<string, string> = {
  kakao: '카카오 로그인을 마치지 못했습니다. 다시 시도해주세요.',
  kakao_admin: '관리자 계정은 카카오로 로그인할 수 없습니다. 이메일로 로그인해주세요.',
  kakao_off: '카카오 로그인은 준비 중입니다. 이메일로 로그인해주세요.',
}

/** 카카오 버튼 — 페이지 이동으로 서버의 OAuth 시작 주소를 연다 */
function KakaoButton({ returnTo, label }: { returnTo: string; label: string }) {
  return (
    <>
      <a className="btn btn-kakao btn-block" href={kakaoStartUrl(returnTo)}>
        {label}
      </a>
      <p className="or small">또는</p>
    </>
  )
}

interface From {
  pathname: string
  state?: unknown
}

/** 카카오 인증 후 서버가 잠시 들고 있는 가입 정보 (httpOnly 쿠키 — URL 에 개인정보를 싣지 않는다) */
interface KakaoPending {
  email: string | null
  emailVerified: boolean
  name: string | null
  phone: string | null
  returnTo: string
}

/** 로그인되면 돌아갈 곳. 리다이렉트는 이 한 곳에서만 한다 (두 군데서 navigate 하면 경합한다) */
function ReturnTo({ fallback = '/mypage' }: { fallback?: string }) {
  const from = (useLocation().state as { from?: From } | null)?.from
  return <Navigate to={from?.pathname ?? fallback} state={from?.state} replace />
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

/** 필수 동의 체크 + 내용 펼쳐 보기 (가입 · 재동의가 같이 쓴다) */
export function Consent({
  checked,
  onChange,
  label,
  error,
  href,
  children,
}: {
  checked: boolean
  onChange: (v: boolean) => void
  label: string
  error?: string
  /** 전문 페이지 — 입력 중인 가입 양식을 잃지 않게 새 탭으로 연다 */
  href: string
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
        <a className="link small" href={href} target="_blank" rel="noopener">
          전문 보기
        </a>
      </details>
      {error && <span className="err" style={{ display: 'block', marginTop: 6 }}>{error}</span>}
    </div>
  )
}

export function Login() {
  useTitle('로그인')
  const { user, login, options } = useAuth()
  const location = useLocation()
  const [params] = useSearchParams()
  // 비밀번호 재설정 · 탈퇴 뒤에 넘어온 안내, 또는 로그인이 필요한 화면에서 온 경우
  const st = location.state as { notice?: string; from?: From } | null
  const notice = st?.notice ?? (st?.from ? '로그인하면 하던 화면으로 바로 돌아갑니다.' : undefined)
  const kakaoErr = KAKAO_ERROR[params.get('error') ?? '']
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  // 2단계 인증을 켠 관리자: 비밀번호가 맞으면 서버가 OTP_REQUIRED 로 코드를 요구한다
  const [needOtp, setNeedOtp] = useState(false)
  const [otp, setOtp] = useState('')
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)

  if (user) return <ReturnTo />

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setErr('')
    try {
      await login(email, password, needOtp ? otp : undefined)
    } catch (x) {
      if (x instanceof ApiError && x.code === 'OTP_REQUIRED') {
        setNeedOtp(true)
        setErr('')
        return
      }
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
      {(kakaoErr || notice) && (
        <p className="note" role={kakaoErr ? 'alert' : 'status'} style={{ marginTop: 32 }}>
          {kakaoErr || notice}
        </p>
      )}
      <form onSubmit={submit} noValidate>
        {options.kakao && <KakaoButton returnTo={st?.from?.pathname ?? '/mypage'} label="카카오로 로그인" />}
        <Field label="이메일" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <Field
          label="비밀번호"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        {needOtp && (
          <Field
            label="2단계 인증 코드 (OTP 앱의 6자리)"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            autoFocus
            value={otp}
            onChange={(e) => setOtp(e.target.value.replace(/[^0-9]/g, ''))}
          />
        )}
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
        {' · '}
        <Link className="link" to="/forgot">
          비밀번호를 잊으셨나요?
        </Link>
      </p>
    </div>
  )
}

export function Signup() {
  useTitle('회원가입')
  const { user, signup, options, replaceUser } = useAuth()
  const location = useLocation()
  const [params] = useSearchParams()
  const st = location.state as { from?: From } | null
  const viaKakao = params.get('via') === 'kakao'
  const [form, setForm] = useState({ email: '', password: '', name: '', phone: '' })
  const [agree, setAgree] = useState({ agreeTerms: false, agreePrivacy: false })
  const [fields, setFields] = useState<Record<string, string>>({})
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)
  // 이메일 인증번호 (쇼핑몰 가입의 "인증번호 받기")
  const [challenge, setChallenge] = useState('')
  const [code, setCode] = useState('')
  const [codeMsg, setCodeMsg] = useState('')
  // 카카오에서 받아 온 정보 (이메일이 카카오 인증이면 인증번호를 건너뛴다)
  const [kakao, setKakao] = useState<KakaoPending | null>(null)

  useEffect(() => {
    if (!viaKakao) return
    api<KakaoPending>('/api/auth/kakao/pending')
      .then((k) => {
        setKakao(k)
        setForm((f) => ({ ...f, email: k.email ?? '', name: k.name ?? '', phone: k.phone ?? '' }))
      })
      .catch(() => setErr('카카오 가입 시간이 지났습니다. 카카오로 다시 시작해주세요.'))
  }, [viaKakao])

  if (user) return <ReturnTo fallback={kakao?.returnTo ?? '/mypage'} />

  const kakaoEmail = !!kakao?.emailVerified && !!kakao.email && kakao.email.toLowerCase() === form.email.trim().toLowerCase()
  const needCode = options.signupEmailCode && !kakaoEmail

  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => {
    setForm({ ...form, [k]: e.target.value })
    // 이메일을 바꾸면 받았던 인증번호는 쓸 수 없다
    if (k === 'email') {
      setChallenge('')
      setCodeMsg('')
    }
  }

  const sendCode = async () => {
    setFields({})
    setCodeMsg('')
    try {
      const r = await api<{ challenge: string }>('/api/auth/signup/email-code', { method: 'POST', body: { email: form.email } })
      setChallenge(r.challenge)
      setCodeMsg(`${form.email} 로 인증번호를 보냈습니다. 10분 안에 입력해주세요.`)
    } catch (x) {
      if (x instanceof ApiError) setFields({ email: x.fields.email ?? x.message })
    }
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (needCode && !challenge) return setFields({ email: '이메일 인증번호를 먼저 받아주세요.' })
    setBusy(true)
    setErr('')
    setFields({})
    const verify = needCode ? { emailChallenge: challenge, emailCode: code } : {}
    try {
      if (kakao) {
        const body = { email: form.email, name: form.name, phone: form.phone, ...agree, ...verify }
        const r = await api<AuthResponse>('/api/auth/kakao/signup', { method: 'POST', body })
        replaceUser(r.user)
      } else await signup({ ...form, ...agree, ...verify })
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
        {kakao ? '카카오로 가입하기' : '회원가입'}
      </h1>
      {kakao && (
        <p className="note" role="status" style={{ marginTop: 24 }}>
          카카오에서 받은 정보로 채웠습니다. 확인하고 약관에 동의하면 가입이 끝납니다.
        </p>
      )}
      <form onSubmit={submit} noValidate>
        {options.kakao && !viaKakao && <KakaoButton returnTo={st?.from?.pathname ?? '/mypage'} label="카카오로 3초 만에 가입하기" />}
        <div className="field">
          <label className="l" htmlFor="su-email">
            이메일{kakaoEmail && ' (카카오 인증 완료)'}
          </label>
          <div className="addr-row">
            <input
              id="su-email"
              type="email"
              autoComplete="email"
              value={form.email}
              onChange={set('email')}
              aria-invalid={!!fields.email}
            />
            {needCode && (
              <button type="button" className="btn btn-secondary btn-sm" onClick={sendCode} disabled={!form.email.trim()}>
                {challenge ? '다시 받기' : '인증번호 받기'}
              </button>
            )}
          </div>
          {(fields.email || codeMsg) && (
            <span className={fields.email ? 'err' : 'small'} style={{ display: 'block', marginTop: 6 }}>
              {fields.email || codeMsg}
            </span>
          )}
        </div>
        {needCode && challenge && (
          <Field
            label="인증번호 (6자리)"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/[^0-9]/g, ''))}
            error={fields.emailCode}
          />
        )}
        {!kakao && (
          <Field
            label="비밀번호 (영문·숫자 포함 8자 이상)"
            type="password"
            autoComplete="new-password"
            value={form.password}
            onChange={set('password')}
            error={fields.password}
          />
        )}
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
          label="[필수] 만 14세 이상이며, 이용약관에 동의합니다."
          error={fields.agreeTerms}
          href="/terms"
        >
          결제는 무통장 입금이며, 예약 요청 후 3일 안에 입금이 확인되지 않으면 자동 취소됩니다. 보증금(대여 요금의 30%)과
          취소·환불은 대여 정책을 따릅니다.
        </Consent>
        <Consent
          checked={agree.agreePrivacy}
          onChange={(v) => setAgree({ ...agree, agreePrivacy: v })}
          label="[필수] 개인정보 수집·이용에 동의합니다."
          error={fields.agreePrivacy}
          href="/privacy"
        >
          수집 항목: 이메일, 비밀번호(암호화 저장 · 카카오 가입은 없음), 이름, 휴대폰 번호 · 카카오 가입 시 카카오 회원번호 · 예약 시
          받는 분, 연락처, 배송 주소, 요청사항
          <br />
          이용 목적: 회원 식별, 로그인, 예약 접수·배송·회수 안내
          <br />
          보유 기간: 탈퇴 시까지 (확정된 거래 기록은 전자상거래법에 따라 5년) · 서버가 국외(싱가포르 등)에 있어 국외 이전이 있습니다
        </Consent>
        <p className="err" role="alert">
          {err}
        </p>
        <Button block arrow type="submit" disabled={busy || (viaKakao && !kakao)}>
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
