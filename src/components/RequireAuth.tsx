import { Navigate, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuth } from '@/lib/auth'

/* 로그인이 필요한 화면. 로그인하지 않았으면 /login 으로 보내고,
   로그인 후 원래 화면(과 그 화면이 받던 state — 예: 예약 날짜)으로 돌아오게 한다.
   admin 이면 ADMIN 만 통과한다. 최종 권한 검사는 항상 backend 가 한다. */

export default function RequireAuth({ children, admin }: { children: ReactNode; admin?: boolean }) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) return <div className="wrap pd-loading" aria-busy="true" />
  if (!user) return <Navigate to="/login" replace state={{ from: { pathname: location.pathname, state: location.state } }} />
  if (admin && user.role !== 'ADMIN') {
    return (
      <div className="wrap pd-loading">
        <p className="label muted">403</p>
        <h1 className="d-m" style={{ marginTop: 16 }}>
          접근 권한이 없습니다.
        </h1>
      </div>
    )
  }
  return <>{children}</>
}
