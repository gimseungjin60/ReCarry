/* Home 섹션 카피. prototype/recarry-prototype-v2.html 문구 그대로. */

import type { CarrierBagProps } from '@/components/CarrierBag'

/** Why RECARRY — 캐리어 한 대의 네 순간 */
export const WHY_FRAMES: { title: string; desc: string; bg: string; bag: CarrierBagProps }[] = [
  {
    title: '버려지기 직전',
    desc: '작은 고장으로 폐기될 캐리어',
    bg: '#d9d5cd',
    bag: { worn: true, handle: 'down', style: { transform: 'rotate(-8deg) translateY(4%)' } },
  },
  { title: '회수', desc: '공항·호텔·가정에서 수거합니다', bg: 'var(--stone)', bag: { worn: true, handle: 'down', tag: true } },
  { title: '복원', desc: '세척·수리·검수를 거칩니다', bg: 'var(--canvas)', bag: { handle: 'down' } },
  { title: '다시 여행', desc: '새로운 사람의 여행을 시작합니다', bg: 'var(--sand)', bag: { tag: true, tagColor: '#141413' } },
]

/** Why Rent — 렌탈을 선택하는 네 가지 이유 */
export const RENT_REASONS = [
  { title: '공간 절약', desc: '1년에 몇 번 쓰는 캐리어가 옷장을 차지하지 않습니다.' },
  { title: '비용 절약', desc: '새 캐리어 대신, 여행하는 기간만큼만 지불합니다.' },
  { title: '새로운 소비 방식', desc: '소유하지 않고, 필요한 만큼 이용합니다.' },
  { title: '자원순환', desc: '캐리어 한 대가 여러 사람의 여행을 합니다.' },
]

/** 브랜드 사진 — 디자인 검증용, 라이선스 미확인 (public/images/README.md) */
export const HOME_IMAGES = {
  hero: { src: '/images/lounge.jpg', alt: '공항 라운지 창밖으로 비행기를 바라보는 여행자' },
  story: { src: '/images/hand.jpg', alt: '캐리어 손잡이를 잡고 하늘 아래로 걸어가는 여행자의 손' },
  rent: { src: '/images/traveler.jpg', alt: '공항 창가에서 오렌지색 캐리어와 함께 이륙하는 비행기를 바라보는 여행자' },
}
