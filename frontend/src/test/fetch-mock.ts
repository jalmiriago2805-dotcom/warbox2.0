import type { MockInstance } from 'vitest'

export type FetchHandler = (init?: RequestInit) => Response | Promise<Response>

/** Value set as the XSRF-TOKEN cookie by the default `GET /api/auth/csrf` handler. */
export const TEST_CSRF_TOKEN = 'test-csrf-token'

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

export function emptyResponse(status = 204): Response {
  return new Response(null, { status })
}

/** Builds an API error response in the contract format `{code,message,fields}`. */
export function errorResponse(
  status: number,
  code: string,
  message = 'Erro',
  fields: Record<string, string> = {},
): Response {
  return jsonResponse({ code, message, fields }, status)
}

export function setCookie(name: string, value: string): void {
  document.cookie = `${name}=${value}; Path=/`
}

export function clearCookie(name: string): void {
  document.cookie = `${name}=; Max-Age=0; Path=/`
}

function requestUrl(input: RequestInfo | URL): string {
  if (typeof input === 'string') return input
  if (input instanceof URL) return input.pathname + input.search
  return input.url
}

/**
 * Replaces global fetch with a router keyed by `'METHOD /path'` (e.g. `'POST /api/auth/login'`).
 * A key with a query string matches exactly; otherwise the query string is ignored.
 * Unmapped routes answer 404 JSON. `GET /api/auth/csrf` defaults to a 204 that sets the
 * XSRF-TOKEN cookie, like the real backend; pass the key to override it.
 */
export function mockFetch(handlers: Record<string, FetchHandler>): MockInstance<typeof fetch> {
  const routes: Record<string, FetchHandler> = {
    'GET /api/auth/csrf': () => {
      setCookie('XSRF-TOKEN', TEST_CSRF_TOKEN)
      return emptyResponse(204)
    },
    ...handlers,
  }
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const method = (init?.method ?? (input instanceof Request ? input.method : 'GET')).toUpperCase()
    const url = requestUrl(input)
    const handler = routes[`${method} ${url}`] ?? routes[`${method} ${url.split('?')[0]}`]
    if (!handler) {
      return errorResponse(404, 'NOT_FOUND', `No mock for ${method} ${url}`)
    }
    return handler(init)
  })
}

/** Calls recorded for a given `'METHOD /path'` key. */
export function callsTo(spy: MockInstance<typeof fetch>, key: string): (RequestInit | undefined)[] {
  const [method, path] = key.split(' ')
  return spy.mock.calls
    .filter(([input, init]) => {
      const m = (init?.method ?? 'GET').toUpperCase()
      return m === method && requestUrl(input).split('?')[0] === path
    })
    .map(([, init]) => init)
}

/** Parses the JSON body sent in a recorded call. */
export function sentJson(init: RequestInit | undefined): unknown {
  return JSON.parse(String(init?.body))
}
