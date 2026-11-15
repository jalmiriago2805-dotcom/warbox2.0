import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createVideo,
  deleteVideo,
  getMyVideo,
  isFinalStatus,
  listMyVideos,
  listPublicVideos,
  publishVideo,
  unpublishVideo,
  updateVideo,
  type CreatorVideo,
  type UpdateVideoRequest,
} from './api'

export const VIDEOS_QUERY_KEY = ['videos'] as const
export const MY_VIDEOS_QUERY_KEY = ['videos', 'mine'] as const

export function myVideoQueryKey(id: string) {
  return ['videos', 'mine', id] as const
}

export function publicVideosQueryKey(handle: string) {
  return ['videos', 'public', handle] as const
}

const POLL_INTERVAL_MS = 5000

export function useMyVideos(opts: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: MY_VIDEOS_QUERY_KEY,
    queryFn: listMyVideos,
    enabled: opts.enabled ?? true,
    retry: false,
    refetchInterval: (query) =>
      query.state.data?.some((v) => !isFinalStatus(v.processingStatus)) ? POLL_INTERVAL_MS : false,
  })
}

/** `data` is the video, `null` when not found, `undefined` while loading. */
export function useMyVideo(id: string, opts: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: myVideoQueryKey(id),
    queryFn: () => getMyVideo(id),
    enabled: opts.enabled ?? true,
    retry: false,
    refetchInterval: (query) => {
      const video = query.state.data
      return video && !isFinalStatus(video.processingStatus) ? POLL_INTERVAL_MS : false
    },
  })
}

/** Public videos only change when the creator publishes, so no polling is needed. */
export function usePublicVideos(handle: string, opts: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: publicVideosQueryKey(handle),
    queryFn: () => listPublicVideos(handle),
    enabled: opts.enabled ?? true,
    retry: false,
  })
}

/** Writes the returned video into its detail cache, then refreshes every video query. */
function useSaveVideo<V>(mutationFn: (variables: V) => Promise<CreatorVideo>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: (video) => {
      queryClient.setQueryData<CreatorVideo | null>(myVideoQueryKey(video.id), video)
      return queryClient.invalidateQueries({ queryKey: VIDEOS_QUERY_KEY })
    },
  })
}

export function useCreateVideo() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createVideo,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: VIDEOS_QUERY_KEY }),
  })
}

export function useUpdateVideo(id: string) {
  return useSaveVideo((req: UpdateVideoRequest) => updateVideo(id, req))
}

export function usePublishVideo() {
  return useSaveVideo(publishVideo)
}

export function useUnpublishVideo() {
  return useSaveVideo(unpublishVideo)
}

export function useDeleteVideo() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: deleteVideo,
    // Not awaited: refetching the deleted video's own query would only produce a 404.
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: VIDEOS_QUERY_KEY })
    },
  })
}
