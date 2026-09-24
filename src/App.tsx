import { useEffect } from 'react'
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import Navbar from '@/components/Navbar'
import Footer from '@/components/Footer'
import Home from '@/pages/Home'
import Product from '@/pages/Product'
import Booking from '@/pages/Booking'
import BookingComplete from '@/pages/BookingComplete'

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
      <div id="app">
        <ScrollToTop />
        <Navbar />
        <main>
          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/product/:size" element={<Product />} />
            <Route path="/booking" element={<Booking />} />
            <Route path="/booking/complete" element={<BookingComplete />} />
            {/* 없는 경로는 홈으로 */}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
        <Footer />
      </div>
    </BrowserRouter>
  )
}
