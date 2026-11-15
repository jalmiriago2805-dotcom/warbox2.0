import { apiFetch, ApiError } from '@/lib/api'

export type VideoAccessType = 'FREE' | 'EXCLUSIVE'
export type VideoVisibility = 'DRAFT' | 'PUBLISHED'
export type VideoProcessingStatus = 'PENDING_UPLOAD' | 'PROCESSING' | 'READY' | 'ERROR'

export type CreatorVideo = {
  id: string
  title: string
  description: string | null
  accessType: VideoAccessType
  visibility: VideoVisibility
  processingStatus: VideoProcessingStatus
  durationSeconds: number | null
  /** Non-null only when accessType = FREE and processingStatus = READY. */
  thumbnailUrl: string | null
  playerUrl: string | null
  createdAt: string
  updatedAt: string
  publishedAt: string | null
}

export type PublicVideo = {
  id: string
  title: string
  description: string | null
  accessType: VideoAccessType
  durationSeconds: number | null
  publishedAt: string
  /** True when EXCLUSIVE; thumbnailUrl and playerUrl are null. */
  locked: boolean
  thumbnailUrl: string | null
  playerUrl: string | null
}

export type UpdateVideoRequest = {
  title: string
  description?: string | null
  accessType: VideoAccessType
}

export type CreateVideoRequest = UpdateVideoRequest & {
  sizeBytes: number
}

export type CreateVideoResponse = {
  video: CreatorVideo
  uploadUrl: string
}

export type VideoErrorCode =
  | 'VIDEO_NOT_FOUND'
  | 'VIDEO_NOT_READY'
  | 'VIDEO_PROVIDER_UNAVAILABLE'
  | 'CREATOR_PROFILE_NOT_FOUND'
  | 'CREATOR_NOT_FOUND'
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

/** Final states never change again, so they need no polling. */
export function isFinalStatus(status: VideoProcessingStatus): boolean {
  return status === 'READY' || status === 'ERROR'
}

function videoPath(id: string): string {
  return `/creators/me/videos/${encodeURIComponent(id)}`
}

export function createVideo(req: CreateVideoRequest): Promise<CreateVideoResponse> {
  return apiFetch<CreateVideoResponse>('/creators/me/videos', { method: 'POST', json: req })
}

export function listMyVideos(): Promise<CreatorVideo[]> {
  return apiFetch<CreatorVideo[]>('/creators/me/videos')
}

/** The creator's video, or null when it does not exist or is not theirs (404). */
export function getMyVideo(id: string): Promise<CreatorVideo | null> {
  return nullOn404(apiFetch<CreatorVideo>(videoPath(id)))
}

export function updateVideo(id: string, req: UpdateVideoRequest): Promise<CreatorVideo> {
  return apiFetch<CreatorVideo>(videoPath(id), { method: 'PUT', json: req })
}

export function publishVideo(id: string): Promise<CreatorVideo> {
  return apiFetch<CreatorVideo>(`${videoPath(id)}/publish`, { method: 'POST' })
}

export function unpublishVideo(id: string): Promise<CreatorVideo> {
  return apiFetch<CreatorVideo>(`${videoPath(id)}/unpublish`, { method: 'POST' })
}

export function deleteVideo(id: string): Promise<void> {
  return apiFetch<void>(videoPath(id), { method: 'DELETE' })
}

/** Published, READY videos of a creator, or null when the handle does not exist (404). */
export function listPublicVideos(handle: string): Promise<PublicVideo[] | null> {
  return nullOn404(apiFetch<PublicVideo[]>(`/creators/${encodeURIComponent(handle)}/videos`))
}
