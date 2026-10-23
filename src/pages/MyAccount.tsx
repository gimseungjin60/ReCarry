import { useState } from 'react'
import type { FormEvent, InputHTMLAttributes, ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import Button from '@/components/Button'
import type { User } from '@/data/types'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { useTitle } from '@/lib/useTitle'

/* 내 계정 — 정보 수정 · 비밀번호 변경 · 탈퇴. 이메일(로그인 ID)은 바꾸지 않는다. */

const errorText = (e: unknown) =>
  e instanceof ApiError ? (Object.values(e.fields)[0] ?? e.message) : '요청을 처리하지 못했습니다.'

/** 폼 하나 = 섹션 하나. 결과 메시지와 진행 상태를 같이 관리한다 */
function AccountForm({
  id,
  title,
  note,
  submitLabel,
  danger,
  onSubmit,
  children,
}: {
  id: string
  title: string
  note?: string
  submitLabel: string
  danger?: boolean
  onSubmit: () => Promise<string | void>
  children: ReactNode
}) {
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  const [msg, setMsg] = useState('')

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setErr('')
    setMsg('')
    try {
      setMsg((await onSubmit()) ?? '')
    } catch (x) {
      setErr(errorText(x))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="adm-sec" aria-labelledby={id}>
      <h2 className="t-title" id={id}>
        {title}
      </h2>
      {note && <p className="small">{note}</p>}
      <form className="adm-form" style={{ maxWidth: 480 }} onSubmit={submit} noValidate>
        {children}
        <p className="err" role="alert">
          {err}
        </p>
        <p className="small" role="status" style={{ color: 'var(--forest)' }}>
          {msg}
        </p>
        <Button small type="submit" variant={danger ? 'secondary' : 'primary'} disabled={busy}>
          {busy ? '처리 중…' : submitLabel}
        </Button>
      </form>
    </section>
  )
}

function Input({ label, ...rest }: { label: string } & InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="field">
      <span className="l">{label}</span>
      <input {...rest} />
    </label>
  )
}

export function MyAccount() {
  useTitle('내 계정')
  const { user, replaceUser } = useAuth()
  const navigate = useNavigate()
  const [profile, setProfile] = useState({ name: user?.name ?? '', phone: user?.phone ?? '' })
  const [pw, setPw] = useState({ currentPassword: '', newPassword: '' })
  const [byePw, setByePw] = useState('')
  const [confirmBye, setConfirmBye] = useState(false)

  if (!user) return null

  return (
    <div className="wrap acct-pg">
      <div className="crumb keep">
        <Link to="/mypage">My page</Link>
        <span>/</span>
        <span style={{ color: 'var(--ink)' }}>내 계정</span>
      </div>
      <h1 className="d-m" style={{ marginTop: 24 }}>
        내 계정
      </h1>
      <p className="small" style={{ marginTop: 8 }}>
        로그인 이메일 {user.email} · 개인정보 처리는{' '}
        <Link className="link" to="/privacy">
          개인정보 처리방침
        </Link>
        을 따릅니다.
      </p>

      <AccountForm
        id="acc-profile"
        title="회원 정보"
        submitLabel="저장"
        onSubmit={async () => {
          replaceUser(await api<User>('/api/users/me', { method: 'PATCH', body: profile }))
          return '저장했습니다.'
        }}
      >
        <Input label="이름" autoComplete="name" maxLength={50} value={profile.name} onChange={(e) => setProfile({ ...profile, name: e.target.value })} />
        <Input
          label="휴대폰 번호"
          inputMode="tel"
          autoComplete="tel"
          value={profile.phone}
          onChange={(e) => setProfile({ ...profile, phone: e.target.value })}
        />
      </AccountForm>

      <AccountForm
        id="acc-password"
        title="비밀번호 변경"
        note="바꾸면 다른 기기의 로그인은 모두 해제됩니다."
        submitLabel="비밀번호 변경"
        onSubmit={async () => {
          await api('/api/users/me/password', { method: 'POST', body: pw })
          setPw({ currentPassword: '', newPassword: '' })
          return '비밀번호를 바꿨습니다.'
        }}
      >
        <Input
          label="현재 비밀번호"
          type="password"
          autoComplete="current-password"
          value={pw.currentPassword}
          onChange={(e) => setPw({ ...pw, currentPassword: e.target.value })}
        />
        <Input
          label="새 비밀번호 (영문·숫자 포함 8자 이상)"
          type="password"
          autoComplete="new-password"
          value={pw.newPassword}
          onChange={(e) => setPw({ ...pw, newPassword: e.target.value })}
        />
      </AccountForm>

      <AccountForm
        id="acc-withdraw"
        title="회원 탈퇴"
        note="확정되었거나 이용 중인 예약이 있으면 반납 후 탈퇴할 수 있습니다. 남은 예약 요청은 취소되고, 회원 정보는 즉시 삭제(비식별)됩니다. 확정된 거래 기록은 법에 따라 보관 후 파기합니다."
        submitLabel="탈퇴하기"
        danger
        onSubmit={async () => {
          if (!confirmBye) throw new ApiError(400, 'CONFIRM', '안내를 확인하고 체크해주세요.')
          await api('/api/users/me/withdraw', { method: 'POST', body: { password: byePw } })
          // 보호된 화면을 먼저 떠난다 — 반대 순서면 로그인 화면으로 튕긴다
          navigate('/login', { replace: true, state: { notice: '탈퇴가 완료되었습니다. 그동안 이용해주셔서 감사합니다.' } })
          replaceUser(null)
        }}
      >
        <Input label="비밀번호 확인" type="password" autoComplete="current-password" value={byePw} onChange={(e) => setByePw(e.target.value)} />
        <label className="agree" style={{ marginTop: 0, marginBottom: 8 }}>
          <input type="checkbox" checked={confirmBye} onChange={(e) => setConfirmBye(e.target.checked)} />
          <span>안내를 확인했으며 탈퇴합니다.</span>
        </label>
      </AccountForm>
    </div>
  )
}
