import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { api, setUnauthorizedHandler } from './api'
import type { AuthResponse, User } from '@/data/types'

/* 로그인 상태. 세션은 backend 의 httpOnly 쿠키에만 있고, 여기서는 사용자 정보만 들고 있다.
   처음 열 때 /api/auth/session 으로 로그인 여부를 확인한다 (비로그인은 401 이 아니라 user=null). */

interface AuthState {
  user: User | null
  /** 세션을 확인하는 중 */
  loading: boolean
  login: (email: string, password: string) => Promise<User>
  signup: (input: { email: string; password: string; name: string; phone: string }) => Promise<User>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    // 보호 API 가 401 을 주면(세션 만료 등) 로그아웃 상태로 바꾼다
    setUnauthorizedHandler(() => setUser(null))
    api<{ user: User | null }>('/api/auth/session')
      .then((s) => setUser(s.user))
      .catch(() => setUser(null))
      .finally(() => setLoading(false))
  }, [])

  const logout = useCallback(async () => {
    // 쿠키는 JS 가 지울 수 없으므로 backend 가 지운다. 실패해도 화면은 로그아웃 상태로
    await api('/api/auth/logout', { method: 'POST' }).catch(() => {})
    setUser(null)
  }, [])

  const value = useMemo<AuthState>(
    () => ({
      user,
      loading,
      logout,
      login: (email, password) =>
        api<AuthResponse>('/api/auth/login', { method: 'POST', body: { email, password } }).then((r) => {
          setUser(r.user)
          return r.user
        }),
      signup: (input) =>
        api<AuthResponse>('/api/auth/signup', { method: 'POST', body: input }).then((r) => {
          setUser(r.user)
          return r.user
        }),
    }),
    [user, loading, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
