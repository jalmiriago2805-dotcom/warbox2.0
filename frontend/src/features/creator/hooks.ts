import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  activateCreatorProfile,
  getMyCreatorProfile,
  getPublicCreator,
  updateCreatorProfile,
  type CreatorProfile,
  type CreatorProfileRequest,
} from './api'

export const MY_CREATOR_PROFILE_QUERY_KEY = ['creators', 'me'] as const

export function publicCreatorQueryKey(handle: string) {
  return ['creators', 'public', handle] as const
}

/** `data` is the profile, `null` when the user has none, `undefined` while loading. */
export function useMyCreatorProfile(opts: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: MY_CREATOR_PROFILE_QUERY_KEY,
    queryFn: getMyCreatorProfile,
    enabled: opts.enabled ?? true,
    retry: false,
    staleTime: 5 * 60 * 1000,
  })
}

export function usePublicCreator(handle: string) {
  return useQuery({
    queryKey: publicCreatorQueryKey(handle),
    queryFn: () => getPublicCreator(handle),
    retry: false,
  })
}

function useSaveProfile(mutationFn: (req: CreatorProfileRequest) => Promise<CreatorProfile>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: (profile) => {
      queryClient.setQueryData<CreatorProfile | null>(MY_CREATOR_PROFILE_QUERY_KEY, profile)
      return queryClient.invalidateQueries({ queryKey: ['creators', 'public'] })
    },
  })
}

export function useActivateCreatorProfile() {
  return useSaveProfile(activateCreatorProfile)
}

export function useUpdateCreatorProfile() {
  return useSaveProfile(updateCreatorProfile)
}
