import { useNavigate } from 'react-router-dom'
import Button from '@/components/Button'
import { kakaoStartUrl } from '@/lib/api'

/* 주문서 마지막 단계의 로그인 선택 — 쇼핑몰 결제 직전 "회원 로그인 / 간편 로그인"과 같은 자리.
   어느 쪽으로 다녀와도 /booking 이 sessionStorage 의 draft 로 이어진다. */

export default function CheckoutLogin({ kakao }: { kakao: boolean }) {
  const navigate = useNavigate()
  const back = { from: { pathname: '/booking' } }

  return (
    <section className="checkout-login" aria-labelledby="ck-title">
      <p className="t-title" id="ck-title">
        로그인하면 고른 내용 그대로 예약 요청이 접수됩니다.
      </p>
      <p className="small" style={{ marginTop: 6 }}>
        예약 확인 · 입금 안내 · 환불을 위해 회원만 예약할 수 있습니다.
      </p>
      <div className="checkout-login-btns">
        {kakao && (
          <a className="btn btn-kakao btn-block" href={kakaoStartUrl('/booking')}>
            카카오로 3초 만에 계속하기
          </a>
        )}
        <Button block variant={kakao ? 'secondary' : 'primary'} onClick={() => navigate('/login', { state: back })}>
          이메일로 로그인
        </Button>
        <Button block variant="secondary" onClick={() => navigate('/signup', { state: back })}>
          회원가입
        </Button>
      </div>
    </section>
  )
}
