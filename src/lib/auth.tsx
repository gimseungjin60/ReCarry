import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { api, setUnauthorizedHandler } from './api'
import type { AuthResponse, User } from '@/data/types'

/* 로그인 상태. 세션은 backend 의 httpOnly 쿠키에만 있고, 여기서는 사용자 정보만 들고 있다.
   처음 열 때 /api/auth/session 으로 로그인 여부를 확인한다 (비로그인은 401 이 아니라 user=null). */

/** 서버 설정에 따라 켜지는 가입 · 로그인 수단 */
export interface AuthOptions {
  /** 카카오 로그인 (KAKAO_* 키가 있을 때) */
  kakao: boolean
  /** 가입 시 이메일 인증번호 (메일을 보낼 수 있을 때) */
  signupEmailCode: boolean
}

interface AuthState {
  user: User | null
  options: AuthOptions
  /** 세션을 확인하는 중 */
  loading: boolean
  /** otp: 2단계 인증을 켠 관리자만 (서버가 OTP_REQUIRED 로 알려준다) */
  login: (email: string, password: string, otp?: string) => Promise<User>
  signup: (input: SignupInput) => Promise<User>
  logout: () => Promise<void>
  /** 서버가 바꾼 사용자 정보로 교체 (정보 수정) · null = 탈퇴로 세션이 끝남 */
  replaceUser: (u: User | null) => void
}

export interface SignupInput {
  email: string
  password: string
  name: string
  phone: string
  /** 필수 동의 — backend 가 다시 검증하고 동의 버전·시각을 기록한다 */
  agreeTerms: boolean
  agreePrivacy: boolean
  /** 이메일 인증번호 (options.signupEmailCode 일 때) */
  emailChallenge?: string
  emailCode?: string
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [options, setOptions] = useState<AuthOptions>({ kakao: false, signupEmailCode: false })

  useEffect(() => {
    // 보호 API 가 401 을 주면(세션 만료 등) 로그아웃 상태로 바꾼다
    setUnauthorizedHandler(() => setUser(null))
    api<{ user: User | null }>('/api/auth/session')
      .then((s) => setUser(s.user))
      .catch(() => setUser(null))
      .finally(() => setLoading(false))
    api<AuthOptions>('/api/auth/options')
      .then(setOptions)
      .catch(() => {})
  }, [])

  const logout = useCallback(async () => {
    // 쿠키는 JS 가 지울 수 없으므로 backend 가 지운다. 실패해도 화면은 로그아웃 상태로
    await api('/api/auth/logout', { method: 'POST' }).catch(() => {})
    setUser(null)
  }, [])

  const value = useMemo<AuthState>(
    () => ({
      user,
      options,
      loading,
      logout,
      replaceUser: setUser,
      login: (email, password, otp) =>
        api<AuthResponse>('/api/auth/login', { method: 'POST', body: { email, password, otp } }).then((r) => {
          setUser(r.user)
          return r.user
        }),
      signup: (input) =>
        api<AuthResponse>('/api/auth/signup', { method: 'POST', body: input }).then((r) => {
          setUser(r.user)
          return r.user
        }),
    }),
    [user, options, loading, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
