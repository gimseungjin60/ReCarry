import { Link } from 'react-router-dom'
import { BUSINESS_INFO } from '@/data/legal'
import { NAV_LINKS, PROTOTYPE_NOTICE } from '@/data/nav'
import { useAnchorNav } from '@/lib/useAnchorNav'
import './Footer.css'

export default function Footer() {
  const goAnchor = useAnchorNav()

  return (
    <footer className="foot">
      <div className="wrap">
        <div className="grid">
          <div>
            <Link to="/" className="logo">
              <span className="re">RE</span>CARRY
            </Link>
            <p className="small" style={{ marginTop: 16, maxWidth: 240 }}>
              버려지는 캐리어를 다시 여행으로.
              <br />
              자원순환형 캐리어 렌탈.
            </p>
          </div>

          <div>
            <p className="label">Service</p>
            <div className="fl">
              {NAV_LINKS.map((l) => (
                <button key={l.id} onClick={() => goAnchor(l.id)}>
                  {l.ko}
                </button>
              ))}
            </div>
          </div>

          <div>
            <p className="label">Support</p>
            <div className="fl">
              <Link to="/policy">대여 정책 (취소·환불·파손·분실)</Link>
              <Link to="/terms">이용약관</Link>
              <Link to="/privacy">
                <b>개인정보 처리방침</b>
              </Link>
            </div>
          </div>

          <div>
            <p className="label">Give a carrier</p>
            <div className="fl">
              <span>쓰지 않는 캐리어가 있나요?</span>
              <span>중고 캐리어 매입 (준비 중)</span>
            </div>
          </div>
        </div>

        {/* 전자상거래법 제10조 — 사업자 정보 표시. 정해진 값만 보여준다 (data/legal.ts 에서 채운다) */}
        <p className="small" style={{ marginTop: 32 }}>
          {BUSINESS_INFO.filter(([, v]) => !v.includes('[확정 필요]'))
            .map(([k, v]) => `${k} ${v}`)
            .join(' · ') || '사업자 정보는 정식 서비스 전에 표시됩니다.'}
        </p>
        <div className="bottom">
          <span>© 2026 RECARRY</span>
          <span>{PROTOTYPE_NOTICE}</span>
        </div>
      </div>
    </footer>
  )
}
