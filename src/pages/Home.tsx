import { useEffect } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import Hero from '@/sections/home/Hero'
import CollectionPreview from '@/sections/home/CollectionPreview'
import HowItWorks from '@/sections/home/HowItWorks'
import CarrierStory from '@/sections/home/CarrierStory'
import Faq from '@/sections/home/Faq'
import Cta from '@/sections/home/Cta'
import '@/sections/home.css'
import { useReveal } from '@/lib/motion'
import { useCatalog } from '@/lib/catalog'

/* Home — 짧게 5개 블록 (2026-10 개편, docs/DESIGN.md §Home).
   첫 화면에서 무엇을 · 얼마에 · 어떻게 받는지 답하고 바로 예약으로. id(#top #collection #how #story #faq)는 nav 앵커다. */

export default function Home() {
  const { state } = useLocation()
  const navigate = useNavigate()
  // 카탈로그가 도착하면 새로 그려진 블록도 reveal 대상에 넣는다
  useReveal(useCatalog().list)

  // 다른 페이지에서 앵커를 눌러 들어온 경우 해당 섹션으로 이동한다
  useEffect(() => {
    const anchor = (state as { anchor?: string } | null)?.anchor
    if (!anchor) return
    document.getElementById(anchor)?.scrollIntoView()
    // 새로고침·뒤로가기 때 다시 점프하지 않도록 state 를 비운다 (router 의 history 정보는 유지)
    navigate('/', { replace: true, state: null, preventScrollReset: true })
  }, [state, navigate])

  return (
    <>
      <Hero />
      <CollectionPreview />
      <HowItWorks />
      <CarrierStory />
      <Faq />
      <Cta />
    </>
  )
}
