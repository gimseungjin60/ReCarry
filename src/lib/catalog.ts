import { useEffect, useState } from 'react'
import { api } from './api'
import { PRESENTATION } from '@/data/carriers'
import type { ApiCarrierModel, Carrier, CarrierSize } from '@/data/types'

/* 캐리어 카탈로그 (GET /api/carriers). 한 번 불러오면 페이지를 옮겨도 다시 받지 않는다.
   API 응답을 화면이 쓰던 Carrier 모양으로 바꿔서, 기존 컴포넌트는 그대로 쓴다. */

const dot = (iso: string) => iso.replaceAll('-', '.')

export function toCarrier(m: ApiCarrierModel): Carrier {
  const f = m.featured
  return {
    key: m.size,
    name: m.name,
    inch: m.inch,
    grade: f.grade,
    price: m.price,
    extra: m.extraNightPrice,
    capacity: m.capacity,
    usage: m.usage,
    dims: m.dims,
    weight: m.weight,
    id: f.code,
    collectedFrom: f.collectedFrom,
    repair: f.repairSummary,
    inspectedAt: f.inspectedAt ? dot(f.inspectedAt) : '—',
    available: m.availableCount,
    headline: m.headline,
    desc: m.description,
    // 타임라인: 첫 줄만 연도까지, 나머지는 월.일 (v1 표기 그대로)
    story: f.events.map((e, i) => ({ date: i === 0 ? dot(e.date) : dot(e.date).slice(5), title: e.title, detail: e.detail })),
    ...PRESENTATION[m.size],
  }
}

let cache: Promise<Carrier[]> | null = null

function load() {
  cache ??= api<ApiCarrierModel[]>('/api/carriers').then((list) => list.map(toCarrier))
  // 실패하면 다음 호출에서 다시 시도한다
  cache.catch(() => (cache = null))
  return cache
}

export interface CatalogState {
  list: Carrier[] | null
  bySize: (size: CarrierSize) => Carrier | undefined
  error: string | null
  retry: () => void
}

export function useCatalog(): CatalogState {
  const [list, setList] = useState<Carrier[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let alive = true
    load()
      .then((l) => alive && (setList(l), setError(null)))
      .catch((e: Error) => alive && setError(e.message))
    return () => {
      alive = false
    }
  }, [attempt])

  return {
    list,
    bySize: (size) => list?.find((c) => c.key === size),
    error,
    retry: () => setAttempt((a) => a + 1),
  }
}
