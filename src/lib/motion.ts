import { useEffect } from 'react'
import type { RefObject } from 'react'

/* 브랜드 motion 두 가지. 라이브러리 없이 IntersectionObserver / rAF 만 쓴다.
   reduced-motion 이면 아무 것도 하지 않고, CSS 도 최종 상태를 기본값으로 둔다. */

export const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches

/**
 * [data-reveal] 요소가 화면에 들어오면 .is-in 을 붙인다 (한 번만).
 * html.reveal-on 이 있어야 CSS 가 요소를 숨기므로, 이 hook 이 안 돌면 전부 그대로 보인다.
 * key 가 바뀌면(예: 상품 사이즈 이동) 새로 그려진 요소를 다시 찾는다.
 */
export function useReveal(key?: unknown) {
  useEffect(() => {
    if (reducedMotion() || !('IntersectionObserver' in window)) return
    const root = document.documentElement
    root.classList.add('reveal-on')

    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries) {
          if (!e.isIntersecting) continue
          e.target.classList.add('is-in')
          io.unobserve(e.target)
        }
      },
      { rootMargin: '0px 0px -10% 0px', threshold: 0.12 },
    )
    document.querySelectorAll('[data-reveal]:not(.is-in)').forEach((el) => io.observe(el))

    return () => {
      io.disconnect()
      root.classList.remove('reveal-on')
    }
  }, [key])
}

/**
 * Scroll-expansion hero (docs/components/scroll-expansion-hero.md 의 Vite 버전).
 * 스크롤을 붙잡지 않는다 — hero 높이의 35% 를 내리는 동안 --expand 가 0 → 1 로 가고,
 * CSS 가 그 값으로 가장자리 여백·radius 를 줄여 사진이 full-bleed 로 펼쳐진다.
 */
export function useHeroExpansion(ref: RefObject<HTMLElement | null>) {
  useEffect(() => {
    const el = ref.current
    if (!el || reducedMotion()) return

    let frame = 0
    const update = () => {
      frame = 0
      const p = Math.min(1, Math.max(0, window.scrollY / (el.offsetHeight * 0.35)))
      el.style.setProperty('--expand', p.toFixed(3))
    }
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(update)
    }
    update()
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll)
    return () => {
      cancelAnimationFrame(frame)
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
      el.style.removeProperty('--expand')
    }
  }, [ref])
}
