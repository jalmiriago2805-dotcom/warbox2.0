// Client-side mirror of the backend field rules. The backend stays the source of truth.

export const MIN_AGE = 18
export const PASSWORD_MIN_LENGTH = 8
export const PASSWORD_MAX_LENGTH = 72
export const PASSWORD_MAX_BYTES = 72
export const NAME_MAX_LENGTH = 100
export const EMAIL_MAX_LENGTH = 254
const MIN_BIRTH_DATE = '1900-01-01'
const AGE_TIME_ZONE = 'America/Sao_Paulo'

export const UNDERAGE_MESSAGE = 'Você precisa ter 18 anos ou mais para criar uma conta.'

type YMD = { y: number; m: number; d: number }

function parseIsoDate(value: string): YMD | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return null
  const [y, m, d] = [Number(match[1]), Number(match[2]), Number(match[3])]
  const date = new Date(Date.UTC(y, m - 1, d))
  if (date.getUTCFullYear() !== y || date.getUTCMonth() !== m - 1 || date.getUTCDate() !== d) {
    return null
  }
  return { y, m, d }
}

/** Today's calendar date in America/Sao_Paulo, as the backend computes it, in `YYYY-MM-DD`. */
export function todayIso(today: Date = new Date()): string {
  // en-CA formats as YYYY-MM-DD.
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: AGE_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(today)
}

/** Age in completed years is >= 18 (someone turning 18 today is an adult). */
export function isAdult(birthDate: string, today: Date = new Date()): boolean {
  const birth = parseIsoDate(birthDate)
  const now = parseIsoDate(todayIso(today))
  if (!birth || !now) return false
  const hadBirthdayThisYear = now.m > birth.m || (now.m === birth.m && now.d >= birth.d)
  const age = now.y - birth.y - (hadBirthdayThisYear ? 0 : 1)
  return age >= MIN_AGE
}

export function validateName(name: string): string | null {
  const trimmed = name.trim()
  if (!trimmed) return 'Informe seu nome.'
  if (trimmed.length > NAME_MAX_LENGTH) return `O nome deve ter no máximo ${NAME_MAX_LENGTH} caracteres.`
  return null
}

export function validateEmail(email: string): string | null {
  const trimmed = email.trim()
  if (!trimmed) return 'Informe seu e-mail.'
  if (trimmed.length > EMAIL_MAX_LENGTH) return `O e-mail deve ter no máximo ${EMAIL_MAX_LENGTH} caracteres.`
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) return 'Informe um e-mail válido.'
  return null
}

export function utf8Length(value: string): number {
  return new TextEncoder().encode(value).length
}

export function validatePassword(password: string): string | null {
  if (!password) return 'Informe uma senha.'
  if (password.length < PASSWORD_MIN_LENGTH) {
    return `A senha deve ter pelo menos ${PASSWORD_MIN_LENGTH} caracteres.`
  }
  if (password.length > PASSWORD_MAX_LENGTH) {
    return `A senha deve ter no máximo ${PASSWORD_MAX_LENGTH} caracteres.`
  }
  if (utf8Length(password) > PASSWORD_MAX_BYTES) {
    return 'A senha é longa demais: acentos e símbolos ocupam mais espaço. Use uma senha mais curta.'
  }
  return null
}

export function validatePasswordConfirmation(password: string, confirmation: string): string | null {
  if (!confirmation) return 'Confirme a senha.'
  if (password !== confirmation) return 'As senhas não coincidem.'
  return null
}

export function validateBirthDate(birthDate: string, today: Date = new Date()): string | null {
  if (!birthDate) return 'Informe sua data de nascimento.'
  if (!parseIsoDate(birthDate)) return 'Informe uma data de nascimento válida.'
  // ISO dates compare correctly as strings.
  if (birthDate < MIN_BIRTH_DATE || birthDate >= todayIso(today)) {
    return 'Informe uma data de nascimento válida.'
  }
  if (!isAdult(birthDate, today)) return UNDERAGE_MESSAGE
  return null
}
