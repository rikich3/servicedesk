import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, NetworkError, buildQuery, describeError, readCookie, request } from './client'

function mockFetch(status: number, body: unknown, contentType = 'application/json') {
  return vi.fn().mockResolvedValue(
    new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { 'Content-Type': contentType } }),
  )
}

describe('api client', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=abc123; path=/'
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('builds query strings and skips empty values', () => {
    expect(buildQuery({ status: 'OPEN', q: '', page: 0, unassigned: undefined })).toBe('?status=OPEN&page=0')
    expect(buildQuery({})).toBe('')
  })

  it('reads cookies by name', () => {
    expect(readCookie('XSRF-TOKEN')).toBe('abc123')
    expect(readCookie('missing')).toBeUndefined()
  })

  it('mirrors the CSRF cookie into the header on mutations and sends credentials', async () => {
    const fetchMock = mockFetch(200, { id: 1 })
    vi.stubGlobal('fetch', fetchMock)
    await request('/api/x', { method: 'POST', body: { a: 1 } })
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(init.credentials).toBe('include')
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('abc123')
    expect(init.body).toBe('{"a":1}')
  })

  it('turns problem details into ApiError with code and field errors', async () => {
    vi.stubGlobal(
      'fetch',
      mockFetch(400, { status: 400, code: 'VALIDATION_ERROR', detail: 'Request validation failed', errors: [{ field: 'title', message: 'must not be blank' }] }, 'application/problem+json'),
    )
    const error = await request('/api/x').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe('VALIDATION_ERROR')
    expect((error as ApiError).status).toBe(400)
    expect((error as ApiError).fieldError('title')).toBe('must not be blank')
  })

  it('raises NetworkError when fetch itself fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    await expect(request('/api/x')).rejects.toBeInstanceOf(NetworkError)
  })

  it('describes known error codes for humans', () => {
    expect(describeError(new ApiError({ status: 409, code: 'STALE_VERSION' }))).toMatch(/Someone else changed/)
    expect(describeError(new ApiError({ status: 403, code: 'FORBIDDEN' }))).toMatch(/permission/)
    expect(describeError(new NetworkError())).toMatch(/Could not reach/)
    expect(describeError(new ApiError({ status: 409, code: 'INVALID_TRANSITION', detail: 'Cannot move a ticket from OPEN to RESOLVED' }))).toBe('Cannot move a ticket from OPEN to RESOLVED')
  })
})
