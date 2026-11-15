import type { VideoAccessType } from './api'

export const TITLE_MAX = 100
export const DESCRIPTION_MAX = 5000
export const MAX_VIDEO_BYTES = 30 * 1024 * 1024 * 1024 // 30 GiB, same limit as the backend

export type VideoMetadataValues = {
  title: string
  description?: string | null
  accessType: VideoAccessType | '' | null | undefined
}

export type VideoFieldErrors = Record<string, string>

export function validateVideoMetadata(values: VideoMetadataValues): VideoFieldErrors {
  const errors: VideoFieldErrors = {}
  const title = values.title.trim()
  if (!title) errors.title = 'Informe o título.'
  else if (title.length > TITLE_MAX) errors.title = 'O título deve ter no máximo 100 caracteres.'

  if ((values.description ?? '').trim().length > DESCRIPTION_MAX)
    errors.description = 'A descrição deve ter no máximo 5000 caracteres.'

  if (values.accessType !== 'FREE' && values.accessType !== 'EXCLUSIVE')
    errors.accessType = 'Escolha o tipo de acesso.'
  return errors
}

export function validateVideoFile(file: File | null | undefined): VideoFieldErrors {
  if (!file) return { file: 'Selecione um arquivo de vídeo.' }
  if (!file.type.startsWith('video/')) return { file: 'O arquivo deve ser um vídeo.' }
  if (file.size < 1 || file.size > MAX_VIDEO_BYTES)
    return { file: 'Arquivo inválido ou maior que 30 GB.' }
  return {}
}
