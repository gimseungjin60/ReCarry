import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import Hero from '@/sections/home/Hero'
import WhyRecarry from '@/sections/home/WhyRecarry'
import CircularProcess from '@/sections/home/CircularProcess'
import CollectionPreview from '@/sections/home/CollectionPreview'
import CarrierStory from '@/sections/home/CarrierStory'
import WhyRent from '@/sections/home/WhyRent'
import Cta from '@/sections/home/Cta'
import '@/sections/home.css'

/* PRD §4 Home — 7개 섹션. 순서와 id(#why #how #collection #story #rent #cta)를 고정한다. */

export default function Home() {
  const { state } = useLocation()

  // 다른 페이지에서 앵커를 눌러 들어온 경우 해당 섹션으로 이동한다
  useEffect(() => {
    const anchor = (state as { anchor?: string } | null)?.anchor
    if (!anchor) return
    document.getElementById(anchor)?.scrollIntoView()
    // 새로고침·뒤로가기 때 다시 점프하지 않도록 state 를 비운다
    window.history.replaceState({}, '')
  }, [state])

  return (
    <>
      <Hero />
      <WhyRecarry />
      <CircularProcess />
      <CollectionPreview />
      <CarrierStory />
      <WhyRent />
      <Cta />
    </>
  )
}
