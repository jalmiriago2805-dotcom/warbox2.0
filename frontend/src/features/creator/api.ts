import { apiFetch, ApiError } from '@/lib/api'

export type CreatorProfile = {
  id: string
  handle: string
  displayName: string
  bio: string | null
  createdAt: string
  updatedAt: string
}

export type PublicCreator = {
  handle: string
  displayName: string
  bio: string | null
  createdAt: string
}

export type CreatorProfileRequest = {
  handle: string
  displayName: string
  bio?: string | null
}

export type CreatorErrorCode =
  | 'CREATOR_NOT_FOUND'
  | 'CREATOR_PROFILE_NOT_FOUND'
  | 'CREATOR_PROFILE_EXISTS'
  | 'HANDLE_TAKEN'
  | 'EMAIL_NOT_VERIFIED'
  | 'VALIDATION_ERROR'

async function nullOn404<T>(request: Promise<T>): Promise<T | null> {
  try {
    return await request
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) return null
    throw err
  }
}

/** Public creator, or null when the handle does not exist (404). */
export function getPublicCreator(handle: string): Promise<PublicCreator | null> {
  return nullOn404(apiFetch<PublicCreator>(`/creators/${encodeURIComponent(handle)}`))
}

/** The logged-in user's creator profile, or null when there is none (404). */
export function getMyCreatorProfile(): Promise<CreatorProfile | null> {
  return nullOn404(apiFetch<CreatorProfile>('/creators/me'))
}

export function activateCreatorProfile(req: CreatorProfileRequest): Promise<CreatorProfile> {
  return apiFetch<CreatorProfile>('/creators/me', { method: 'POST', json: req })
}

export function updateCreatorProfile(req: CreatorProfileRequest): Promise<CreatorProfile> {
  return apiFetch<CreatorProfile>('/creators/me', { method: 'PUT', json: req })
}
