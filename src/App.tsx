import { useEffect } from 'react'
import { BrowserRouter, Route, Routes, useLocation } from 'react-router-dom'
import Navbar from '@/components/Navbar'
import Footer from '@/components/Footer'
import Home from '@/pages/Home'
import Product from '@/pages/Product'
import Booking from '@/pages/Booking'
import BookingComplete from '@/pages/BookingComplete'
import { Login, Signup } from '@/pages/Auth'
import { MyBooking, MyPage } from '@/pages/MyPage'
import { MyAccount } from '@/pages/MyAccount'
import { Admin, AdminBooking } from '@/pages/Admin'
import { AdminCarrier } from '@/pages/AdminCarrier'
import Legal from '@/pages/Legal'
import ConsentPage from '@/pages/ConsentPage'
import Forgot from '@/pages/Forgot'
import VerifyEmail from '@/pages/VerifyEmail'
import Guide from '@/pages/Guide'
import Help from '@/pages/Help'
import NotFound from '@/pages/NotFound'
import RequireAuth from '@/components/RequireAuth'
import { AuthProvider } from '@/lib/auth'

/* 페이지를 옮기면 맨 위에서 시작한다.
   단, 앵커로 진입한 경우는 Home 이 직접 스크롤하므로 건드리지 않는다. */
function ScrollToTop() {
  const { pathname, hash, state } = useLocation()
  useEffect(() => {
    if ((state as { anchor?: string } | null)?.anchor) return
    // /guide#pay 같은 링크는 그 섹션으로
    const el = hash && document.getElementById(hash.slice(1))
    if (el) el.scrollIntoView()
    else window.scrollTo(0, 0)
  }, [pathname, hash, state])
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
            <Route path="/booking" element={<Booking />} />
            <Route path="/booking/complete" element={<RequireAuth><BookingComplete /></RequireAuth>} />
            <Route path="/login" element={<Login />} />
            <Route path="/signup" element={<Signup />} />
            <Route path="/mypage" element={<RequireAuth><MyPage /></RequireAuth>} />
            <Route path="/mypage/bookings/:bookingNumber" element={<RequireAuth><MyBooking /></RequireAuth>} />
            <Route path="/mypage/account" element={<RequireAuth><MyAccount /></RequireAuth>} />
            <Route path="/admin" element={<RequireAuth admin><Admin /></RequireAuth>} />
            <Route path="/admin/bookings/:bookingNumber" element={<RequireAuth admin><AdminBooking /></RequireAuth>} />
            <Route path="/admin/carriers/:code" element={<RequireAuth admin><AdminCarrier /></RequireAuth>} />
            <Route path="/consent" element={<ConsentPage />} />
            <Route path="/forgot" element={<Forgot />} />
            <Route path="/verify-email" element={<VerifyEmail />} />
            <Route path="/guide" element={<Guide />} />
            <Route path="/help" element={<Help />} />
            <Route path="/terms" element={<Legal doc="terms" />} />
            <Route path="/privacy" element={<Legal doc="privacy" />} />
            <Route path="/policy" element={<Legal doc="policy" />} />
            {/* 없는 경로는 404 로 알린다 */}
            <Route path="*" element={<NotFound />} />
          </Routes>
        </main>
        <Footer />
      </div>
      </AuthProvider>
    </BrowserRouter>
  )
}
