import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { ApiError } from '@/lib/api'
import type { SignupRequest } from '@/features/auth/api'
import { AuthCard, FormField } from '@/features/auth/components/FormField'
import { messageForError } from '@/features/auth/errorMessages'
import { useMe, useSignup } from '@/features/auth/hooks'
import {
  todayIso,
  validateBirthDate,
  validateEmail,
  validateName,
  validatePassword,
} from '@/features/auth/validation'

type Field = keyof SignupRequest
type FieldErrors = Partial<Record<Field, string>>

const TERMS_MESSAGE = 'Você precisa aceitar os Termos de Uso e a Política de Privacidade.'

function validate(form: SignupRequest): FieldErrors {
  const errors: FieldErrors = {}
  const name = validateName(form.name)
  if (name) errors.name = name
  const email = validateEmail(form.email)
  if (email) errors.email = email
  const password = validatePassword(form.password)
  if (password) errors.password = password
  const birthDate = validateBirthDate(form.birthDate)
  if (birthDate) errors.birthDate = birthDate
  if (!form.acceptedTerms) errors.acceptedTerms = TERMS_MESSAGE
  return errors
}

const FIELD_FOR_CODE: Partial<Record<string, Field>> = {
  EMAIL_TAKEN: 'email',
  UNDERAGE: 'birthDate',
  TERMS_NOT_ACCEPTED: 'acceptedTerms',
}

export function SignupPage() {
  const navigate = useNavigate()
  const { data: me } = useMe()
  const signup = useSignup()
  const [form, setForm] = useState<SignupRequest>({
    name: '',
    email: '',
    password: '',
    birthDate: '',
    acceptedTerms: false,
  })
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)

  if (me) return <Navigate to="/" replace />

  const set = <K extends Field>(key: K, value: SignupRequest[K]) => {
    setForm((f) => ({ ...f, [key]: value }))
    setErrors((e) => ({ ...e, [key]: undefined }))
  }

  const onSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setFormError(null)
    const clientErrors = validate(form)
    setErrors(clientErrors)
    if (Object.keys(clientErrors).length > 0) return

    const payload: SignupRequest = {
      name: form.name.trim(),
      email: form.email.trim(),
      password: form.password,
      birthDate: form.birthDate,
      acceptedTerms: form.acceptedTerms,
    }
    signup.mutate(payload, {
      onSuccess: () => navigate('/', { replace: true }),
      onError: (err) => {
        const field = err instanceof ApiError ? FIELD_FOR_CODE[err.code] : undefined
        if (field) {
          setErrors({ [field]: messageForError(err) })
        } else if (
          err instanceof ApiError &&
          err.code === 'VALIDATION_ERROR' &&
          Object.keys(err.fields).length > 0
        ) {
          setErrors(err.fields as FieldErrors)
        } else {
          setFormError(messageForError(err))
        }
      },
    })
  }

  return (
    <AuthCard title="Criar conta" description="Assine e acompanhe seus criadores favoritos.">
      <form noValidate onSubmit={onSubmit} className="flex flex-col gap-4" aria-label="Criar conta">
        {formError ? (
          <Alert variant="destructive">
            <AlertDescription>{formError}</AlertDescription>
          </Alert>
        ) : null}
        <FormField
          id="signup-name"
          label="Nome"
          autoComplete="name"
          value={form.name}
          error={errors.name}
          onChange={(e) => set('name', e.target.value)}
        />
        <FormField
          id="signup-email"
          label="E-mail"
          type="email"
          autoComplete="email"
          value={form.email}
          error={errors.email}
          onChange={(e) => set('email', e.target.value)}
        />
        <FormField
          id="signup-password"
          label="Senha"
          type="password"
          autoComplete="new-password"
          value={form.password}
          error={errors.password}
          onChange={(e) => set('password', e.target.value)}
        />
        <FormField
          id="signup-birth-date"
          label="Data de nascimento"
          type="date"
          autoComplete="bday"
          min="1900-01-01"
          max={todayIso()}
          value={form.birthDate}
          error={errors.birthDate}
          onChange={(e) => set('birthDate', e.target.value)}
        />
        <div className="flex flex-col gap-2">
          <div className="flex items-start gap-2">
            <Checkbox
              id="signup-terms"
              checked={form.acceptedTerms}
              onCheckedChange={(checked) => set('acceptedTerms', checked === true)}
              aria-invalid={errors.acceptedTerms ? true : undefined}
              aria-describedby={errors.acceptedTerms ? 'signup-terms-error' : undefined}
            />
            <Label htmlFor="signup-terms" className="leading-snug font-normal">
              Tenho 18 anos ou mais e aceito os Termos de Uso e a Política de Privacidade
            </Label>
          </div>
          {errors.acceptedTerms ? (
            <p id="signup-terms-error" className="text-destructive text-sm">
              {errors.acceptedTerms}
            </p>
          ) : null}
        </div>
        <Button type="submit" disabled={signup.isPending}>
          {signup.isPending ? 'Criando conta…' : 'Criar conta'}
        </Button>
      </form>
      <p className="text-muted-foreground text-sm">
        Já tem uma conta?{' '}
        <Link to="/entrar" className="text-primary underline-offset-4 hover:underline">
          Entrar
        </Link>
      </p>
    </AuthCard>
  )
}
