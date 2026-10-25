import { useCallback } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { reducedMotion } from './motion'

/* 홈 내부 섹션으로 이동한다 ('/' 로 시작하면 그 페이지로).
   - 이미 홈이면 그 자리에서 스크롤
   - 다른 페이지면 홈으로 이동한 뒤 Home 이 location.state.anchor 를 읽어 스크롤
   prototype 의 S.pendingAnchor 와 같은 역할. */
export function useAnchorNav() {
  const navigate = useNavigate()
  const { pathname } = useLocation()

  return useCallback(
    (id: string) => {
      // 메뉴의 페이지 링크 (이용 안내 · 고객센터)
      if (id.startsWith('/')) navigate(id)
      else if (pathname === '/') {
        document.getElementById(id)?.scrollIntoView({ behavior: reducedMotion() ? 'auto' : 'smooth' })
      } else {
        navigate('/', { state: { anchor: id } })
      }
    },
    [navigate, pathname],
  )
}
