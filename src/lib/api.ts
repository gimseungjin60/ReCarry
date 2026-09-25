/* backend REST 호출은 모두 여기를 지난다.
   - base URL: VITE_API_BASE_URL (비우면 같은 origin — 개발 중엔 Vite proxy 가 /api 를 :8080 으로 넘긴다)
   - 인증: backend 가 httpOnly 쿠키로 세션을 준다. JS 는 토큰을 보지도 저장하지도 않는다
   - CSRF: 상태를 바꾸는 요청에는 X-Recarry-Client 헤더를 붙인다 (backend 가 확인)
   - 오류: backend ErrorResponse { code, message, fields } 를 ApiError 로 바꾼다 */

const BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

export class ApiError extends Error {
  status: number
  code: string
  fields: Record<string, string>

  constructor(status: number, code: string, message: string, fields?: Record<string, string> | null) {
    super(message)
    this.status = status
    this.code = code
    this.fields = fields ?? {}
  }
}

/** 세션이 만료·위조되어 401 이 나면 호출된다 (auth 가 등록) */
let onUnauthorized: () => void = () => {}
export const setUnauthorizedHandler = (fn: () => void) => {
  onUnauthorized = fn
}

interface Options {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE'
  body?: unknown
}

export async function api<T>(path: string, { method = 'GET', body }: Options = {}): Promise<T> {
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') headers['X-Recarry-Client'] = 'web'

  let res: Response
  try {
    res = await fetch(BASE + path, {
      method,
      headers,
      // 다른 origin 의 backend 에도 세션 쿠키를 보내려면 include 가 필요하다
      credentials: 'include',
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'NETWORK', '서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.')
  }

  const text = await res.text()
  const data = text ? safeJson(text) : null
  if (!res.ok) {
    const e = (data ?? {}) as { code?: string; message?: string; fields?: Record<string, string> }
    if (res.status === 401) onUnauthorized()
    throw new ApiError(res.status, e.code ?? 'HTTP_' + res.status, e.message ?? '요청을 처리하지 못했습니다.', e.fields)
  }
  // 200 인데 JSON 이 아니면 API 가 아닌 곳(예: 정적 호스팅의 index.html)에 닿은 것 — VITE_API_BASE_URL 확인
  if (text && data === null) {
    throw new ApiError(res.status, 'BAD_RESPONSE', '서버 응답을 읽을 수 없습니다. 잠시 후 다시 시도해주세요.')
  }
  return data as T
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}
