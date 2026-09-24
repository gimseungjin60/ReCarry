import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import Button from './Button'
import { NAV_LINKS } from '@/data/nav'
import { useAnchorNav } from '@/lib/useAnchorNav'
import './Navbar.css'

/* 모바일 전체화면 메뉴. Navbar 의 '메뉴' 버튼에서만 열린다. */

interface Props {
  onClose: () => void
}

export default function MenuSheet({ onClose }: Props) {
  const goAnchor = useAnchorNav()

  // 시트가 열린 동안 뒤 페이지가 스크롤되지 않게 한다
  useEffect(() => {
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = prev
    }
  }, [])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const go = (id: string) => {
    onClose()
    goAnchor(id)
  }

  return (
    <div className="menu-sheet" role="dialog" aria-modal="true" aria-label="메뉴">
      <div className="nav-in">
        <Link to="/" className="logo" onClick={onClose}>
          <span className="re">RE</span>CARRY
        </Link>
        <button onClick={onClose} style={{ fontSize: 14, fontWeight: 500 }}>
          닫기
        </button>
      </div>

      <div className="mlinks">
        {NAV_LINKS.map((l) => (
          <button key={l.id} onClick={() => go(l.id)}>
            {l.ko}
          </button>
        ))}
      </div>

      <div className="menu-foot">
        <Button block arrow onClick={() => go('collection')}>
          캐리어 둘러보기
        </Button>
        <p className="small" style={{ marginTop: 16 }}>
          버려지는 캐리어를 다시 여행으로.
        </p>
      </div>
    </div>
  )
}
