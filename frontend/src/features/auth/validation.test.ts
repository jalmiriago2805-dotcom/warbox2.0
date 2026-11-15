import {
  isAdult,
  UNDERAGE_MESSAGE,
  validateBirthDate,
  validateEmail,
  validateName,
  validatePassword,
  validatePasswordConfirmation,
} from './validation'

// Noon in São Paulo on 2026-11-15, so the calendar date is unambiguous.
const TODAY = new Date('2026-11-15T12:00:00-03:00')

describe('isAdult', () => {
  it('accepts someone turning 18 today', () => {
    expect(isAdult('2008-11-15', TODAY)).toBe(true)
  })

  it('rejects someone turning 18 tomorrow', () => {
    expect(isAdult('2008-11-16', TODAY)).toBe(false)
  })

  it('accepts someone who turned 18 yesterday and older people', () => {
    expect(isAdult('2008-11-14', TODAY)).toBe(true)
    expect(isAdult('1970-01-01', TODAY)).toBe(true)
  })

  it('uses the São Paulo calendar date', () => {
    // 01:00 UTC on Nov 16 is still Nov 15 in São Paulo.
    expect(isAdult('2008-11-16', new Date('2026-11-16T01:00:00Z'))).toBe(false)
    expect(isAdult('2008-11-16', new Date('2026-11-16T03:00:00Z'))).toBe(true)
  })

  it('handles Feb 29 birthdays in non-leap years', () => {
    expect(isAdult('2008-02-29', new Date('2026-02-28T12:00:00-03:00'))).toBe(false)
    expect(isAdult('2008-02-29', new Date('2026-03-01T12:00:00-03:00'))).toBe(true)
  })

  it('returns false for malformed dates', () => {
    expect(isAdult('', TODAY)).toBe(false)
    expect(isAdult('2000-02-30', TODAY)).toBe(false)
    expect(isAdult('15/11/2000', TODAY)).toBe(false)
  })
})

describe('validatePassword', () => {
  it('rejects 7 characters and accepts 8', () => {
    expect(validatePassword('1234567')).toBe('A senha deve ter pelo menos 8 caracteres.')
    expect(validatePassword('12345678')).toBeNull()
  })

  it('accepts 72 ASCII characters and rejects 73', () => {
    expect(validatePassword('a'.repeat(72))).toBeNull()
    expect(validatePassword('a'.repeat(73))).toBe('A senha deve ter no máximo 72 caracteres.')
  })

  it('rejects passwords over 72 UTF-8 bytes even with fewer characters', () => {
    // "é" takes 2 bytes: 36 of them = 72 bytes (ok), 37 = 74 bytes (too long).
    expect(validatePassword('é'.repeat(36))).toBeNull()
    expect('é'.repeat(37).length).toBeLessThanOrEqual(72)
    expect(validatePassword('é'.repeat(37))).toMatch(/longa demais/)
    expect(validatePassword('a'.repeat(71) + 'é')).toMatch(/longa demais/)
  })

  it('requires a value', () => {
    expect(validatePassword('')).toBe('Informe uma senha.')
  })
})

describe('validatePasswordConfirmation', () => {
  it('requires matching passwords', () => {
    expect(validatePasswordConfirmation('abcdefgh', 'abcdefgh')).toBeNull()
    expect(validatePasswordConfirmation('abcdefgh', 'abcdefgi')).toBe('As senhas não coincidem.')
  })
})

describe('validateName', () => {
  it('requires 1 to 100 characters after trim', () => {
    expect(validateName('   ')).toBe('Informe seu nome.')
    expect(validateName(' Ana ')).toBeNull()
    expect(validateName('a'.repeat(100))).toBeNull()
    expect(validateName('a'.repeat(101))).toMatch(/100 caracteres/)
  })
})

describe('validateEmail', () => {
  it('validates presence, format and length', () => {
    expect(validateEmail('')).toBe('Informe seu e-mail.')
    expect(validateEmail('ana')).toBe('Informe um e-mail válido.')
    expect(validateEmail(' Ana@Example.com ')).toBeNull()
    expect(validateEmail(`${'a'.repeat(250)}@b.co`)).toMatch(/254 caracteres/)
  })
})

describe('validateBirthDate', () => {
  it('requires a valid past date from 1900 on', () => {
    expect(validateBirthDate('', TODAY)).toBe('Informe sua data de nascimento.')
    expect(validateBirthDate('1899-12-31', TODAY)).toBe('Informe uma data de nascimento válida.')
    expect(validateBirthDate('2026-11-15', TODAY)).toBe('Informe uma data de nascimento válida.')
    expect(validateBirthDate('1900-01-01', TODAY)).toBeNull()
  })

  it('blocks minors', () => {
    expect(validateBirthDate('2008-11-16', TODAY)).toBe(UNDERAGE_MESSAGE)
    expect(validateBirthDate('2008-11-15', TODAY)).toBeNull()
  })
})
