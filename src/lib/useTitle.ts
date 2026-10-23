import { useEffect } from 'react'

/** 페이지별 브라우저 탭 제목. 비우면 기본 제목 */
export function useTitle(title?: string) {
  useEffect(() => {
    document.title = title ? `${title} | RECARRY` : 'RECARRY — 검수 끝난 캐리어, 출발 전날 문 앞으로'
  }, [title])
}
