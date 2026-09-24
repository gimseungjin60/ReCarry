import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Button from './Button'
import MenuSheet from './MenuSheet'
import { NAV_LINKS } from '@/data/nav'
import { useAnchorNav } from '@/lib/useAnchorNav'
import './Navbar.css'

export default function Navbar() {
  const [scrolled, setScrolled] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const goAnchor = useAnchorNav()

  // 8px 이상 내려가면 하단 hairline 을 그린다 (prototype 과 동일)
  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  return (
    <>
      <header className={'nav' + (scrolled ? ' is-scrolled' : '')}>
        <div className="wrap nav-in">
          <Link to="/" className="logo" aria-label="RECARRY 홈">
            <span className="re">RE</span>CARRY
          </Link>

          <nav className="nav-links" aria-label="주요 메뉴">
            {NAV_LINKS.map((l) => (
              <button key={l.id} onClick={() => goAnchor(l.id)}>
                {l.label}
              </button>
            ))}
          </nav>

          <div className="nav-actions">
            <Button small onClick={() => goAnchor('collection')}>
              캐리어 둘러보기
            </Button>
            <button className="nav-menu" onClick={() => setMenuOpen(true)} aria-label="메뉴 열기">
              메뉴
            </button>
          </div>
        </div>
      </header>

      {menuOpen && <MenuSheet onClose={() => setMenuOpen(false)} />}
    </>
  )
}
