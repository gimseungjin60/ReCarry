import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import Button from '@/components/Button'
import type { User } from '@/data/types'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { useTitle } from '@/lib/useTitle'

/* 가입 인증 메일의 링크 (/verify-email?token=…). 로그인하지 않아도 인증된다 */

export default function VerifyEmail() {
  useTitle('이메일 인증')
  const [params] = useSearchParams()
  const { user, replaceUser } = useAuth()
  const [state, setState] = useState<'busy' | 'ok' | string>('busy')
  const sent = useRef(false)

  useEffect(() => {
    if (sent.current) return   // StrictMode 이중 실행 방지
    sent.current = true
    api('/api/auth/verify-email', { method: 'POST', body: { token: params.get('token') ?? '' } })
      .then(() => setState('ok'))
      .catch((e) => setState(e instanceof ApiError ? e.message : '인증하지 못했습니다.'))
  }, [params])

  // 로그인해 있으면 화면의 회원 정보도 인증 완료로 바꾼다
  useEffect(() => {
    if (state === 'ok' && user?.needsEmailVerification) api<User>('/api/users/me').then(replaceUser).catch(() => {})
  }, [state, user, replaceUser])

  return (
    <div className="wrap auth-pg">
      <p className="label muted">Account</p>
      <h1 className="d-m" style={{ marginTop: 12 }}>
        이메일 인증
      </h1>
      {state === 'busy' && <p className="small" style={{ marginTop: 24 }}>확인하는 중입니다…</p>}
      {state === 'ok' && (
        <>
          <p style={{ marginTop: 24 }}>이메일 인증이 완료되었습니다. 이제 캐리어를 예약할 수 있습니다.</p>
          <div style={{ marginTop: 32 }}>
            <Button to={user ? '/booking' : '/login'} arrow>
              {user ? '예약하러 가기' : '로그인'}
            </Button>
          </div>
        </>
      )}
      {state !== 'busy' && state !== 'ok' && (
        <>
          <p className="note" role="alert" style={{ marginTop: 24 }}>
            {state}
          </p>
          <div style={{ marginTop: 32 }}>
            <Button to="/mypage" variant="secondary">
              My Page 에서 다시 받기
            </Button>
          </div>
        </>
      )}
    </div>
  )
}
