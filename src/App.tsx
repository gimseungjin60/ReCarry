import { useEffect } from 'react'
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import Navbar from '@/components/Navbar'
import Footer from '@/components/Footer'
import Home from '@/pages/Home'
import Product from '@/pages/Product'
import Booking from '@/pages/Booking'
import BookingComplete from '@/pages/BookingComplete'
import { Login, Signup } from '@/pages/Auth'
import { MyBooking, MyPage } from '@/pages/MyPage'
import { Admin, AdminBooking } from '@/pages/Admin'
import RequireAuth from '@/components/RequireAuth'
import { AuthProvider } from '@/lib/auth'

/* 페이지를 옮기면 맨 위에서 시작한다.
   단, 앵커로 진입한 경우는 Home 이 직접 스크롤하므로 건드리지 않는다. */
function ScrollToTop() {
  const { pathname, state } = useLocation()
  useEffect(() => {
    if ((state as { anchor?: string } | null)?.anchor) return
    window.scrollTo(0, 0)
  }, [pathname, state])
  return null
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
      <div id="app">
        <ScrollToTop />
        <Navbar />
        <main>
          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/product/:size" element={<Product />} />
            {/* 예약·내 정보·관리는 로그인이 필요하다 (최종 권한 검사는 backend) */}
            <Route path="/booking" element={<RequireAuth><Booking /></RequireAuth>} />
            <Route path="/booking/complete" element={<RequireAuth><BookingComplete /></RequireAuth>} />
            <Route path="/login" element={<Login />} />
            <Route path="/signup" element={<Signup />} />
            <Route path="/mypage" element={<RequireAuth><MyPage /></RequireAuth>} />
            <Route path="/mypage/bookings/:bookingNumber" element={<RequireAuth><MyBooking /></RequireAuth>} />
            <Route path="/admin" element={<RequireAuth admin><Admin /></RequireAuth>} />
            <Route path="/admin/bookings/:bookingNumber" element={<RequireAuth admin><AdminBooking /></RequireAuth>} />
            {/* 없는 경로는 홈으로 */}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
        <Footer />
      </div>
      </AuthProvider>
    </BrowserRouter>
  )
}
