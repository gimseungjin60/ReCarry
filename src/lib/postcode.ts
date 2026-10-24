/* 카카오(다음) 우편번호 서비스 — 무료 · 키 없음. 누를 때만 스크립트를 받고, 팝업 창으로 연다 (CSP: vercel.json script-src). */

const SRC = 'https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js'

interface PostcodeResult {
  roadAddress: string
  jibunAddress: string
  buildingName: string
}
interface PostcodeApi {
  Postcode: new (o: { oncomplete: (d: PostcodeResult) => void }) => { open: () => void }
}
declare global {
  interface Window {
    daum?: PostcodeApi
  }
}

let loading: Promise<PostcodeApi> | null = null

function load(): Promise<PostcodeApi> {
  if (window.daum?.Postcode) return Promise.resolve(window.daum)
  loading ??= new Promise((resolve, reject) => {
    const s = document.createElement('script')
    s.src = SRC
    s.async = true
    s.onload = () => (window.daum?.Postcode ? resolve(window.daum) : reject(new Error('postcode')))
    s.onerror = () => {
      loading = null
      s.remove()
      reject(new Error('postcode'))
    }
    document.head.appendChild(s)
  })
  return loading
}

/** 검색 창을 열고, 고른 도로명 주소(건물명 포함)를 넘긴다 */
export async function searchAddress(onPick: (address: string) => void) {
  const daum = await load()
  new daum.Postcode({
    oncomplete: (d) => {
      const base = d.roadAddress || d.jibunAddress
      onPick(d.buildingName ? `${base} (${d.buildingName})` : base)
    },
  }).open()
}
