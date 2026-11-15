import { apiFetch } from '@/lib/api'

export type HealthResponse = { status: 'ok' }

export function getHealth(): Promise<HealthResponse> {
  return apiFetch<HealthResponse>('/health')
}
