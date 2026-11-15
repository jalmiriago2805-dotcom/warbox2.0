import { normalizeHandle, validateCreatorProfile } from './validation'

const ok = { handle: 'davi', displayName: 'Davi', bio: null }

describe('validateCreatorProfile', () => {
  it('accepts a valid profile', () => {
    expect(validateCreatorProfile(ok)).toEqual({})
    expect(validateCreatorProfile({ ...ok, handle: 'a-b_9', bio: 'x'.repeat(500) })).toEqual({})
  })

  it('requires the handle', () => {
    expect(validateCreatorProfile({ ...ok, handle: '  ' }).handle).toBe('Informe um nome de usuário.')
  })

  it.each(['ab', 'a'.repeat(31), 'a b c', 'ábc', '-abc', 'abc_', 'ab$'])('rejects format %s', (handle) => {
    expect(validateCreatorProfile({ ...ok, handle }).handle).toMatch(/^Use de 3 a 30/)
  })

  it('accepts 3 and 30 characters', () => {
    expect(validateCreatorProfile({ ...ok, handle: 'abc' }).handle).toBeUndefined()
    expect(validateCreatorProfile({ ...ok, handle: 'a'.repeat(30) }).handle).toBeUndefined()
  })

  it('rejects reserved handles, case-insensitively', () => {
    expect(validateCreatorProfile({ ...ok, handle: 'Admin' }).handle).toBe(
      'Este nome de usuário não está disponível.',
    )
  })

  it('normalizes handles', () => {
    expect(normalizeHandle('  DaVi ')).toBe('davi')
    expect(validateCreatorProfile({ ...ok, handle: ' DAVI ' })).toEqual({})
  })

  it('validates displayName', () => {
    expect(validateCreatorProfile({ ...ok, displayName: ' ' }).displayName).toBe('Informe o nome de exibição.')
    expect(validateCreatorProfile({ ...ok, displayName: 'x'.repeat(61) }).displayName).toBe(
      'O nome de exibição deve ter no máximo 60 caracteres.',
    )
    expect(validateCreatorProfile({ ...ok, displayName: 'x'.repeat(60) }).displayName).toBeUndefined()
  })

  it('validates bio length', () => {
    expect(validateCreatorProfile({ ...ok, bio: 'x'.repeat(501) }).bio).toBe(
      'A bio deve ter no máximo 500 caracteres.',
    )
  })
})
