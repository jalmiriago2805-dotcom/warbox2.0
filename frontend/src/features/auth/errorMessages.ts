import { ApiError } from '@/lib/api'
import type { AuthErrorCode } from './api'
import { UNDERAGE_MESSAGE } from './validation'

export const GENERIC_ERROR_MESSAGE = 'Algo deu errado. Tente novamente em instantes.'
export const NETWORK_ERROR_MESSAGE = 'Não foi possível conectar ao servidor. Verifique sua conexão.'

const MESSAGES: Record<AuthErrorCode, string> = {
  VALIDATION_ERROR: 'Verifique os dados informados.',
  UNDERAGE: UNDERAGE_MESSAGE,
  TERMS_NOT_ACCEPTED: 'Você precisa aceitar os Termos de Uso e a Política de Privacidade.',
  INVALID_OR_EXPIRED_TOKEN: 'Este link é inválido ou expirou.',
  UNAUTHENTICATED: 'Sua sessão expirou. Entre novamente.',
  INVALID_CREDENTIALS: 'E-mail ou senha incorretos.',
  CSRF_INVALID: 'Sua sessão expirou. Recarregue a página e tente novamente.',
  FORBIDDEN: 'Você não tem permissão para fazer isso.',
  EMAIL_NOT_VERIFIED: 'Confirme seu e-mail para continuar.',
  EMAIL_TAKEN: 'Este e-mail já está cadastrado.',
  EMAIL_ALREADY_VERIFIED: 'Seu e-mail já está confirmado.',
  TOO_MANY_REQUESTS: 'Muitas tentativas. Aguarde um minuto e tente novamente.',
}

/** pt-BR message for any error thrown by the auth API functions. */
export function messageForError(err: unknown): string {
  if (err instanceof ApiError) {
    return MESSAGES[err.code as AuthErrorCode] ?? GENERIC_ERROR_MESSAGE
  }
  if (err instanceof TypeError) return NETWORK_ERROR_MESSAGE
  return GENERIC_ERROR_MESSAGE
}
