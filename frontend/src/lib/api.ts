export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`/api${path}`, {
    credentials: 'include',
    ...init,
    headers: { Accept: 'application/json', ...init?.headers },
  })
  if (!res.ok) {
    throw new ApiError(res.status, `Request failed with status ${res.status}`)
  }
  return (await res.json()) as T
}
