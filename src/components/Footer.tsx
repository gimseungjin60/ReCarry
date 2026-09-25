import { Link } from 'react-router-dom'
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
              <span>이용 안내</span>
              <span>파손·분실 정책 (준비 중)</span>
              <span>자주 묻는 질문</span>
            </div>
          </div>

          <div>
            <p className="label">Give a carrier</p>
            <div className="fl">
              <span>쓰지 않는 캐리어가 있나요?</span>
              <span>캐리어 기증 (준비 중)</span>
            </div>
          </div>
        </div>

        <div className="bottom">
          <span>© 2026 RECARRY</span>
          <span>{PROTOTYPE_NOTICE}</span>
        </div>
      </div>
    </footer>
  )
}
