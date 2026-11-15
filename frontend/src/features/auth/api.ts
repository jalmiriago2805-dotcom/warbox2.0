import { apiFetch, ApiError } from '@/lib/api'

export type Me = { id: string; name: string; email: string; emailVerified: boolean }

export type SignupRequest = {
  name: string
  email: string
  password: string
  /** ISO date `YYYY-MM-DD`. */
  birthDate: string
  acceptedTerms: boolean
}

export type LoginRequest = { email: string; password: string }

export type PasswordResetConfirmRequest = { token: string; newPassword: string }

export type AuthErrorCode =
  | 'VALIDATION_ERROR'
  | 'UNDERAGE'
  | 'TERMS_NOT_ACCEPTED'
  | 'INVALID_OR_EXPIRED_TOKEN'
  | 'UNAUTHENTICATED'
  | 'INVALID_CREDENTIALS'
  | 'CSRF_INVALID'
  | 'FORBIDDEN'
  | 'EMAIL_NOT_VERIFIED'
  | 'EMAIL_TAKEN'
  | 'EMAIL_ALREADY_VERIFIED'
  | 'TOO_MANY_REQUESTS'

/** Current user, or null when there is no session (401). */
export async function getMe(): Promise<Me | null> {
  try {
    return await apiFetch<Me>('/auth/me')
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) return null
    throw err
  }
}

export function signup(req: SignupRequest): Promise<Me> {
  return apiFetch<Me>('/auth/signup', { method: 'POST', json: req })
}

export function login(req: LoginRequest): Promise<Me> {
  return apiFetch<Me>('/auth/login', { method: 'POST', json: req })
}

export function logout(): Promise<void> {
  return apiFetch<void>('/auth/logout', { method: 'POST' })
}

export function verifyEmail(token: string): Promise<void> {
  return apiFetch<void>('/auth/verify-email', { method: 'POST', json: { token } })
}

export function resendVerification(): Promise<void> {
  return apiFetch<void>('/auth/verify-email/resend', { method: 'POST' })
}

export function requestPasswordReset(email: string): Promise<void> {
  return apiFetch<void>('/auth/password-reset/request', { method: 'POST', json: { email } })
}

export function confirmPasswordReset(req: PasswordResetConfirmRequest): Promise<void> {
  return apiFetch<void>('/auth/password-reset/confirm', { method: 'POST', json: req })
}
