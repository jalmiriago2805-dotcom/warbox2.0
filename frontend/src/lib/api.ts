/** Error thrown by {@link apiFetch} for any non-2xx response. */
export class ApiError extends Error {
  status: number
  /** Machine-readable code from the API error body (`UNKNOWN` when the body is not JSON). */
  code: string
  /** Per-field messages (pt-BR), filled only for `VALIDATION_ERROR`. */
  fields: Record<string, string>

  constructor(
    status: number,
    message: string,
    code: string = 'UNKNOWN',
    fields: Record<string, string> = {},
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fields = fields
  }
}

export type ApiFetchInit = Omit<RequestInit, 'body'> & {
  body?: BodyInit
  /** Serialized with JSON.stringify and sent with `Content-Type: application/json`. */
  json?: unknown
}

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const CSRF_ENDPOINT = '/api/auth/csrf'
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

/**
 * Returns the raw value of a cookie, or null when it is missing or empty.
 * The value is not URI-decoded: Spring expects the CSRF header to carry the cookie as-is.
 */
export function readCookie(name: string): string | null {
  if (typeof document === 'undefined' || !document.cookie) return null
  for (const part of document.cookie.split(';')) {
    const trimmed = part.trim()
    const eq = trimmed.indexOf('=')
    if (eq === -1) continue
    if (trimmed.slice(0, eq) === name) {
      const value = trimmed.slice(eq + 1)
      return value === '' ? null : value
    }
  }
  return null
}

async function fetchCsrfCookie(): Promise<void> {
  await fetch(CSRF_ENDPOINT, { credentials: 'include' })
}

/** Reads the CSRF cookie at request time (it rotates on login/signup), bootstrapping it if missing. */
async function csrfToken(): Promise<string | null> {
  const current = readCookie(CSRF_COOKIE)
  if (current) return current
  await fetchCsrfCookie()
  return readCookie(CSRF_COOKIE)
}

async function readBody(res: Response): Promise<unknown> {
  if (res.status === 204) return undefined
  const text = await res.text()
  if (!text) return undefined
  try {
    return JSON.parse(text) as unknown
  } catch {
    return text
  }
}

function isErrorBody(
  body: unknown,
): body is { code: string; message?: unknown; fields?: unknown } {
  return typeof body === 'object' && body !== null && typeof (body as { code?: unknown }).code === 'string'
}

function toApiError(status: number, body: unknown): ApiError {
  if (isErrorBody(body)) {
    const message =
      typeof body.message === 'string' && body.message
        ? body.message
        : `Request failed with status ${status}`
    const fields =
      typeof body.fields === 'object' && body.fields !== null
        ? (body.fields as Record<string, string>)
        : {}
    return new ApiError(status, message, body.code, fields)
  }
  return new ApiError(status, `Request failed with status ${status}`)
}

/**
 * The only way the app talks to the backend. `path` is relative to `/api`.
 * Sends cookies, injects the CSRF header on mutating methods, resolves `undefined`
 * for 204/empty bodies and throws {@link ApiError} for non-2xx responses.
 */
export async function apiFetch<T>(path: string, init: ApiFetchInit = {}): Promise<T> {
  const { json, headers: initHeaders, body: initBody, ...rest } = init
  const method = (rest.method ?? 'GET').toUpperCase()
  const mutating = !SAFE_METHODS.has(method)

  const body: BodyInit | undefined = json !== undefined ? JSON.stringify(json) : initBody

  const send = async (): Promise<Response> => {
    const headers = new Headers(initHeaders)
    if (!headers.has('Accept')) headers.set('Accept', 'application/json')
    if (json !== undefined) headers.set('Content-Type', 'application/json')
    if (mutating) {
      const token = await csrfToken()
      if (token) headers.set(CSRF_HEADER, token)
    }
    return fetch(`/api${path}`, { credentials: 'include', ...rest, headers, body })
  }

  let res = await send()
  let data = await readBody(res)

  if (mutating && res.status === 403 && isErrorBody(data) && data.code === 'CSRF_INVALID') {
    // The cookie is stale (rotated or expired): drop it so the server issues a fresh one,
    // then retry exactly once.
    document.cookie = `${CSRF_COOKIE}=; Max-Age=0; Path=/`
    await fetchCsrfCookie()
    res = await send()
    data = await readBody(res)
  }

  if (!res.ok) throw toApiError(res.status, data)
  return data as T
}
