import { apiFetch, ApiError, readCookie } from './api'
import {
  callsTo,
  clearCookie,
  emptyResponse,
  errorResponse,
  jsonResponse,
  mockFetch,
  setCookie,
  TEST_CSRF_TOKEN,
} from '@/test/fetch-mock'

function headerOf(init: RequestInit | undefined, name: string): string | null {
  return new Headers(init?.headers).get(name)
}

beforeEach(() => clearCookie('XSRF-TOKEN'))

describe('apiFetch', () => {
  it('returns the JSON body and calls /api/<path> with credentials include', async () => {
    const spy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ status: 'ok' }))
    await expect(apiFetch('/health')).resolves.toEqual({ status: 'ok' })
    expect(spy).toHaveBeenCalledWith(
      '/api/health',
      expect.objectContaining({ credentials: 'include' }),
    )
  })

  it('throws ApiError with the status on a 500 response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ error: 'x' }, 500))
    const err = await apiFetch('/health').catch((e: unknown) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect((err as ApiError).status).toBe(500)
    expect((err as ApiError).code).toBe('UNKNOWN')
    expect((err as ApiError).fields).toEqual({})
  })

  it('exposes code, message and fields from the error body', async () => {
    mockFetch({
      'POST /api/auth/signup': () =>
        errorResponse(400, 'VALIDATION_ERROR', 'Dados inválidos.', { email: 'E-mail inválido.' }),
    })
    const err = (await apiFetch('/auth/signup', { method: 'POST', json: {} }).catch(
      (e: unknown) => e,
    )) as ApiError
    expect(err).toBeInstanceOf(ApiError)
    expect(err.status).toBe(400)
    expect(err.code).toBe('VALIDATION_ERROR')
    expect(err.message).toBe('Dados inválidos.')
    expect(err.fields).toEqual({ email: 'E-mail inválido.' })
  })

  it('uses code UNKNOWN when the error has no body', async () => {
    mockFetch({ 'GET /api/x': () => emptyResponse(502) })
    const err = (await apiFetch('/x').catch((e: unknown) => e)) as ApiError
    expect(err.status).toBe(502)
    expect(err.code).toBe('UNKNOWN')
  })

  it('resolves undefined on 204', async () => {
    setCookie('XSRF-TOKEN', 'abc')
    mockFetch({ 'POST /api/auth/logout': () => emptyResponse(204) })
    await expect(apiFetch('/auth/logout', { method: 'POST' })).resolves.toBeUndefined()
  })

  it('resolves undefined on a 200 with an empty body', async () => {
    mockFetch({ 'GET /api/empty': () => new Response('', { status: 200 }) })
    await expect(apiFetch('/empty')).resolves.toBeUndefined()
  })

  it('serializes the json option and sets Content-Type', async () => {
    setCookie('XSRF-TOKEN', 'abc')
    const spy = mockFetch({ 'POST /api/auth/login': () => jsonResponse({ id: '1' }) })
    await apiFetch('/auth/login', { method: 'POST', json: { email: 'a@b.com', password: 'x' } })
    const [init] = callsTo(spy, 'POST /api/auth/login')
    expect(init?.body).toBe(JSON.stringify({ email: 'a@b.com', password: 'x' }))
    expect(headerOf(init, 'Content-Type')).toBe('application/json')
    expect(headerOf(init, 'Accept')).toBe('application/json')
  })

  it('sends X-XSRF-TOKEN equal to the cookie on POST', async () => {
    setCookie('XSRF-TOKEN', 'cookie-value-123')
    const spy = mockFetch({ 'POST /api/auth/logout': () => emptyResponse() })
    await apiFetch('/auth/logout', { method: 'POST' })
    const [init] = callsTo(spy, 'POST /api/auth/logout')
    expect(headerOf(init, 'X-XSRF-TOKEN')).toBe('cookie-value-123')
    expect(callsTo(spy, 'GET /api/auth/csrf')).toHaveLength(0)
  })

  it('reads the cookie at request time (no caching across rotations)', async () => {
    const spy = mockFetch({ 'POST /api/a': () => emptyResponse() })
    setCookie('XSRF-TOKEN', 'first')
    await apiFetch('/a', { method: 'POST' })
    setCookie('XSRF-TOKEN', 'second')
    await apiFetch('/a', { method: 'POST' })
    const calls = callsTo(spy, 'POST /api/a')
    expect(calls.map((init) => headerOf(init, 'X-XSRF-TOKEN'))).toEqual(['first', 'second'])
  })

  it('does not send X-XSRF-TOKEN on GET nor bootstrap the cookie', async () => {
    setCookie('XSRF-TOKEN', 'abc')
    const spy = mockFetch({ 'GET /api/health': () => jsonResponse({ status: 'ok' }) })
    await apiFetch('/health')
    const [init] = callsTo(spy, 'GET /api/health')
    expect(headerOf(init, 'X-XSRF-TOKEN')).toBeNull()
    expect(callsTo(spy, 'GET /api/auth/csrf')).toHaveLength(0)
  })

  it('calls /api/auth/csrf first when the cookie is missing', async () => {
    const spy = mockFetch({ 'POST /api/auth/login': () => jsonResponse({ id: '1' }) })
    await apiFetch('/auth/login', { method: 'POST', json: {} })
    const urls = spy.mock.calls.map(([input]) => input)
    expect(urls).toEqual(['/api/auth/csrf', '/api/auth/login'])
    expect(spy.mock.calls[0][1]).toEqual(expect.objectContaining({ credentials: 'include' }))
    const [init] = callsTo(spy, 'POST /api/auth/login')
    expect(headerOf(init, 'X-XSRF-TOKEN')).toBe(TEST_CSRF_TOKEN)
  })

  it('retries exactly once after 403 CSRF_INVALID with a renewed token', async () => {
    setCookie('XSRF-TOKEN', 'stale')
    const responses = [errorResponse(403, 'CSRF_INVALID'), jsonResponse({ ok: true })]
    const spy = mockFetch({ 'POST /api/a': () => responses.shift()! })
    await expect(apiFetch('/a', { method: 'POST' })).resolves.toEqual({ ok: true })
    const calls = callsTo(spy, 'POST /api/a')
    expect(calls).toHaveLength(2)
    expect(headerOf(calls[0], 'X-XSRF-TOKEN')).toBe('stale')
    expect(headerOf(calls[1], 'X-XSRF-TOKEN')).toBe(TEST_CSRF_TOKEN)
    expect(callsTo(spy, 'GET /api/auth/csrf')).toHaveLength(1)
  })

  it('gives up after the single retry and throws the CSRF error', async () => {
    setCookie('XSRF-TOKEN', 'stale')
    const spy = mockFetch({ 'POST /api/a': () => errorResponse(403, 'CSRF_INVALID') })
    const err = (await apiFetch('/a', { method: 'POST' }).catch((e: unknown) => e)) as ApiError
    expect(err.code).toBe('CSRF_INVALID')
    expect(callsTo(spy, 'POST /api/a')).toHaveLength(2)
  })

  it('does not retry other 403 errors', async () => {
    setCookie('XSRF-TOKEN', 'abc')
    const spy = mockFetch({ 'POST /api/a': () => errorResponse(403, 'FORBIDDEN') })
    const err = (await apiFetch('/a', { method: 'POST' }).catch((e: unknown) => e)) as ApiError
    expect(err.code).toBe('FORBIDDEN')
    expect(callsTo(spy, 'POST /api/a')).toHaveLength(1)
  })
})

describe('readCookie', () => {
  it('returns the raw value or null', () => {
    setCookie('XSRF-TOKEN', 'abc-123')
    setCookie('other', 'x')
    expect(readCookie('XSRF-TOKEN')).toBe('abc-123')
    expect(readCookie('missing')).toBeNull()
    clearCookie('other')
  })
})
