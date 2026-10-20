/**
 * Minimal fetch wrapper that speaks the backend's conventions:
 *  - same-origin session cookie (credentials: 'include')
 *  - CSRF double-submit: mirror the XSRF-TOKEN cookie into the X-XSRF-TOKEN header
 *  - RFC 9457 problem details surfaced as ApiError with a stable `code`
 */

export interface FieldError {
  field: string
  message: string
}

export interface ProblemDetail {
  status: number
  title?: string
  detail?: string
  code?: string
  errors?: FieldError[]
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly errors: FieldError[]

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${problem.status}`)
    this.name = 'ApiError'
    this.status = problem.status
    this.code = problem.code ?? 'UNKNOWN'
    this.errors = problem.errors ?? []
  }

  fieldError(field: string): string | undefined {
    return this.errors.find((e) => e.field === field)?.message
  }
}

/** Raised when the request never reached the server (offline, DNS, proxy down). */
export class NetworkError extends Error {
  constructor() {
    super('Could not reach the server. Check your connection and try again.')
    this.name = 'NetworkError'
  }
}

export function readCookie(name: string): string | undefined {
  const match = document.cookie
    .split('; ')
    .find((row) => row.startsWith(name + '='))
  return match ? decodeURIComponent(match.substring(name.length + 1)) : undefined
}

type Method = 'GET' | 'POST' | 'PATCH' | 'DELETE'

export interface RequestOptions {
  method?: Method
  body?: unknown
  query?: Record<string, string | number | boolean | undefined | ''>
}

let csrfPrimed = false

async function primeCsrf(): Promise<void> {
  if (csrfPrimed || readCookie('XSRF-TOKEN')) {
    csrfPrimed = true
    return
  }
  try {
    await fetch('/api/auth/csrf', { credentials: 'include' })
  } finally {
    csrfPrimed = true
  }
}

export function buildQuery(query?: RequestOptions['query']): string {
  if (!query) return ''
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === '' || value === null) continue
    params.set(key, String(value))
  }
  const s = params.toString()
  return s ? `?${s}` : ''
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') {
    await primeCsrf()
    const token = readCookie('XSRF-TOKEN')
    if (token) headers['X-XSRF-TOKEN'] = token
  }

  let response: Response
  try {
    response = await fetch(path + buildQuery(options.query), {
      method,
      headers,
      credentials: 'include',
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    })
  } catch {
    throw new NetworkError()
  }

  if (response.status === 204) return undefined as T

  const text = await response.text()
  let payload: unknown = undefined
  if (text) {
    try {
      payload = JSON.parse(text)
    } catch {
      payload = undefined
    }
  }

  if (!response.ok) {
    const problem = (payload as ProblemDetail | undefined) ?? { status: response.status }
    throw new ApiError({ ...problem, status: problem.status ?? response.status })
  }
  return payload as T
}

export const api = {
  get: <T>(path: string, query?: RequestOptions['query']) => request<T>(path, { query }),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PATCH', body }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}

/** Human-readable message for any thrown value, used by error banners. */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'STALE_VERSION':
        return 'Someone else changed this ticket while you were editing. Reload to see the latest version.'
      case 'INVALID_TRANSITION':
        return error.message
      case 'UNAUTHENTICATED':
        return 'Your session has expired. Please sign in again.'
      case 'FORBIDDEN':
        return 'You do not have permission to do that.'
      case 'NOT_FOUND':
        return 'We could not find what you were looking for.'
      case 'VALIDATION_ERROR':
        return 'Some fields are invalid. Please review the form.'
      default:
        return error.message
    }
  }
  if (error instanceof NetworkError) return error.message
  if (error instanceof Error) return error.message
  return 'Something went wrong.'
}
