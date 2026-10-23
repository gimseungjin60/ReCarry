import { useState } from 'react'
import type { FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import Button from '@/components/Button'
import { LEGAL_EFFECTIVE } from '@/data/legal'
import type { User } from '@/data/types'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { Consent } from '@/pages/Auth'

/* 약관·개인정보 처리방침이 바뀐 뒤 처음 들어온 회원의 재동의. RequireAuth 가 이리로 보낸다. */

interface From {
  pathname: string
  state?: unknown
}

export default function ConsentPage() {
  const { user, loading, replaceUser } = useAuth()
  const navigate = useNavigate()
  const from = (useLocation().state as { from?: From } | null)?.from
  const [agree, setAgree] = useState({ agreeTerms: false, agreePrivacy: false })
  const [fields, setFields] = useState<Record<string, string>>({})
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)

  if (loading) return <div className="wrap pd-loading" aria-busy="true" />
  if (!user) return <Navigate to="/login" replace />
  if (!user.consentRequired) return <Navigate to={from?.pathname ?? '/mypage'} state={from?.state} replace />

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setErr('')
    setFields({})
    try {
      // 동의가 기록되면 위의 분기가 원래 화면으로 돌려보낸다
      replaceUser(await api<User>('/api/users/me/consent', { method: 'POST', body: agree }))
    } catch (x) {
      if (x instanceof ApiError) {
        setFields(x.fields)
        setErr(Object.keys(x.fields).length ? '' : x.message)
      } else setErr('저장하지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="wrap auth-pg">
      <p className="label muted">Updated terms</p>
      <h1 className="d-m" style={{ marginTop: 12 }}>
        약관이 바뀌었습니다
      </h1>
      <p className="small" style={{ marginTop: 12 }}>
        {LEGAL_EFFECTIVE} 부터 시행하는 이용약관과 개인정보 처리방침에 동의해야 예약을 이어갈 수 있습니다. 동의하지 않으려면 My Page 의
        내 계정에서 탈퇴할 수 있습니다.
      </p>
      <form onSubmit={submit} noValidate>
        <Consent
          checked={agree.agreeTerms}
          onChange={(v) => setAgree({ ...agree, agreeTerms: v })}
          label="[필수] 만 14세 이상이며, 이용약관에 동의합니다."
          error={fields.agreeTerms}
          href="/terms"
        >
          예약 요청 3일 자동 만료, 보증금(대여 요금의 30%), 취소·환불·회사 사정 취소 배상 기준이 추가되었습니다.
        </Consent>
        <Consent
          checked={agree.agreePrivacy}
          onChange={(v) => setAgree({ ...agree, agreePrivacy: v })}
          label="[필수] 개인정보 수집·이용에 동의합니다."
          error={fields.agreePrivacy}
          href="/privacy"
        >
          보유 기간, 처리 위탁·국외 이전(서버 위치), 탈퇴 시 처리 방법이 명시되었습니다.
        </Consent>
        <p className="err" role="alert">
          {err}
        </p>
        <Button block arrow type="submit" disabled={busy}>
          {busy ? '저장 중…' : '동의하고 계속하기'}
        </Button>
      </form>
      <p className="small" style={{ marginTop: 24 }}>
        <button type="button" className="link" onClick={() => navigate('/mypage/account', { replace: true })}>
          동의하지 않고 탈퇴하기
        </button>
      </p>
    </div>
  )
}
