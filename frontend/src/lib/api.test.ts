import { apiFetch, ApiError } from './api'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

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
  })
})
