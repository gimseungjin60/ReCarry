import { useState } from 'react'
import Button from '@/components/Button'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'

/* 이메일 인증 전 회원 안내 + 인증 메일 다시 받기. 서버가 인증을 요구할 때만 보인다 (My Page · 예약 화면). */

export default function VerifyEmailNotice() {
  const { user } = useAuth()
  const [msg, setMsg] = useState('')
  const [busy, setBusy] = useState(false)
  if (!user?.needsEmailVerification) return null

  const resend = async () => {
    setBusy(true)
    try {
      await api('/api/users/me/verify-email', { method: 'POST' })
      setMsg(`${user.email} 로 인증 메일을 다시 보냈습니다.`)
    } catch (e) {
      setMsg(e instanceof ApiError ? e.message : '메일을 보내지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="note" role="status" style={{ marginTop: 20, alignItems: 'center', flexWrap: 'wrap' }}>
      <span className="mono">EMAIL</span>
      <span style={{ flex: 1, minWidth: 200 }}>
        {msg || '가입하신 이메일로 보낸 링크를 눌러 인증을 마치면 예약할 수 있습니다.'}
      </span>
      <Button variant="secondary" small onClick={resend} disabled={busy}>
        {busy ? '보내는 중…' : '인증 메일 다시 받기'}
      </Button>
    </div>
  )
}
