import type { CreatorProfileRequest } from './api'

export const HANDLE_MAX = 30
export const DISPLAY_NAME_MAX = 60
export const BIO_MAX = 500
export const HANDLE_PATTERN = /^[a-z0-9][a-z0-9_-]{1,28}[a-z0-9]$/
export const RESERVED_HANDLES: ReadonlySet<string> = new Set([
  'me', 'admin', 'administrador', 'api', 'app', 'c', 'criador', 'criadores', 'explorar',
  'entrar', 'cadastro', 'sair', 'conta', 'configuracoes', 'ajuda', 'suporte', 'termos',
  'privacidade', 'warbox', 'assinar', 'assinaturas', 'videos', 'null', 'undefined',
])

export function normalizeHandle(s: string): string {
  return s.trim().toLowerCase()
}

export type CreatorFieldErrors = Partial<Record<'handle' | 'displayName' | 'bio', string>>

export function validateCreatorProfile(values: CreatorProfileRequest): CreatorFieldErrors {
  const errors: CreatorFieldErrors = {}
  const handle = normalizeHandle(values.handle)
  if (!handle) errors.handle = 'Informe um nome de usuário.'
  else if (!HANDLE_PATTERN.test(handle))
    errors.handle =
      "Use de 3 a 30 caracteres: letras minúsculas, números, '-' ou '_', começando e terminando com letra ou número."
  else if (RESERVED_HANDLES.has(handle)) errors.handle = 'Este nome de usuário não está disponível.'

  const displayName = values.displayName.trim()
  if (!displayName) errors.displayName = 'Informe o nome de exibição.'
  else if (displayName.length > DISPLAY_NAME_MAX)
    errors.displayName = 'O nome de exibição deve ter no máximo 60 caracteres.'

  if ((values.bio ?? '').trim().length > BIO_MAX)
    errors.bio = 'A bio deve ter no máximo 500 caracteres.'
  return errors
}
