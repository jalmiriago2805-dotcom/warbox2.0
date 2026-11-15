import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  confirmPasswordReset,
  getMe,
  login,
  logout,
  requestPasswordReset,
  resendVerification,
  signup,
  verifyEmail,
  type Me,
} from './api'

export const ME_QUERY_KEY = ['auth', 'me'] as const

function isMeKey(key: readonly unknown[]): boolean {
  return key.length === ME_QUERY_KEY.length && key.every((part, i) => part === ME_QUERY_KEY[i])
}

/** `data` is the current user, `null` when logged out, `undefined` while loading. */
export function useMe() {
  return useQuery({
    queryKey: ME_QUERY_KEY,
    queryFn: getMe,
    retry: false,
    staleTime: 5 * 60 * 1000,
  })
}

export function useSignup() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: signup,
    onSuccess: (me) => queryClient.setQueryData<Me | null>(ME_QUERY_KEY, me),
  })
}

export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: login,
    onSuccess: (me) => queryClient.setQueryData<Me | null>(ME_QUERY_KEY, me),
  })
}

export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: logout,
    onSuccess: () => {
      // Drop everything cached for the previous user, keep "me" as logged out.
      queryClient.removeQueries({ predicate: (q) => !isMeKey(q.queryKey) })
      queryClient.setQueryData<Me | null>(ME_QUERY_KEY, null)
    },
  })
}

export function useVerifyEmail() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: verifyEmail,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY }),
  })
}

export function useResendVerification() {
  return useMutation({ mutationFn: resendVerification })
}

export function useRequestPasswordReset() {
  return useMutation({ mutationFn: requestPasswordReset })
}

export function useConfirmPasswordReset() {
  return useMutation({ mutationFn: confirmPasswordReset })
}
