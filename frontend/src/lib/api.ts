// Thin fetch wrapper: attaches the bearer token, parses JSON, and surfaces backend error messages.

const TOKEN_KEY = 'mft_token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

interface RequestOptions {
  method?: string
  body?: unknown
  // When set, the body is sent as-is (e.g. FormData) instead of JSON.
  raw?: boolean
}

export async function api<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {}
  const token = getToken()
  if (token) headers['Authorization'] = `Bearer ${token}`

  let body: BodyInit | undefined
  if (opts.body !== undefined) {
    if (opts.raw) {
      body = opts.body as BodyInit
    } else {
      headers['Content-Type'] = 'application/json'
      body = JSON.stringify(opts.body)
    }
  }

  const res = await fetch(path, { method: opts.method || 'GET', headers, body })

  if (res.status === 401) {
    clearToken()
    window.dispatchEvent(new CustomEvent('mft-unauthorized'))
    throw new ApiError(401, 'Session expired. Please sign in again.')
  }

  const text = await res.text()
  const data = text ? safeJson(text) : null

  if (!res.ok) {
    const message =
      (data && (data.message || data.error)) || `Request failed (${res.status})`
    throw new ApiError(res.status, message)
  }
  return data as T
}

function safeJson(text: string): any {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}
