import { MAX_VIDEO_BYTES, validateVideoFile, validateVideoMetadata } from './validation'

function fileOf(type: string, size: number): File {
  const file = new File(['x'], 'v.mp4', { type })
  Object.defineProperty(file, 'size', { value: size })
  return file
}

describe('validateVideoMetadata', () => {
  it('accepts valid values', () => {
    expect(validateVideoMetadata({ title: 'Aula 1', description: null, accessType: 'FREE' })).toEqual({})
  })

  it('requires a title after trimming', () => {
    expect(validateVideoMetadata({ title: '   ', accessType: 'FREE' }).title).toBe('Informe o título.')
  })

  it('limits the title to 100 characters', () => {
    expect(validateVideoMetadata({ title: 'a'.repeat(100), accessType: 'FREE' })).toEqual({})
    expect(validateVideoMetadata({ title: 'a'.repeat(101), accessType: 'FREE' }).title).toBe(
      'O título deve ter no máximo 100 caracteres.',
    )
  })

  it('limits the description to 5000 characters', () => {
    expect(
      validateVideoMetadata({ title: 'a', description: 'b'.repeat(5001), accessType: 'FREE' }).description,
    ).toBe('A descrição deve ter no máximo 5000 caracteres.')
  })

  it('requires an access type', () => {
    expect(validateVideoMetadata({ title: 'a', accessType: '' }).accessType).toBe('Escolha o tipo de acesso.')
  })
})

describe('validateVideoFile', () => {
  it('requires a file', () => {
    expect(validateVideoFile(null).file).toBe('Selecione um arquivo de vídeo.')
  })

  it('accepts a video up to 30 GB', () => {
    expect(validateVideoFile(fileOf('video/mp4', MAX_VIDEO_BYTES))).toEqual({})
  })

  it('rejects non-video files', () => {
    expect(validateVideoFile(fileOf('image/png', 10)).file).toBe('O arquivo deve ser um vídeo.')
  })

  it('rejects empty and oversized files', () => {
    expect(validateVideoFile(fileOf('video/mp4', 0)).file).toBe('Arquivo inválido ou maior que 30 GB.')
    expect(validateVideoFile(fileOf('video/mp4', MAX_VIDEO_BYTES + 1)).file).toBe(
      'Arquivo inválido ou maior que 30 GB.',
    )
  })
})
