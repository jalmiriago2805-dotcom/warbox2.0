import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { AuthCard, FormField } from '@/features/auth/components/FormField'
import { messageForError } from '@/features/auth/errorMessages'
import { useLogin, useMe } from '@/features/auth/hooks'

type FieldErrors = { email?: string; password?: string }

export function LoginPage() {
  const navigate = useNavigate()
  const { data: me } = useMe()
  const login = useLogin()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)

  if (me) return <Navigate to="/" replace />

  const onSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setFormError(null)
    const clientErrors: FieldErrors = {}
    if (!email.trim()) clientErrors.email = 'Informe seu e-mail.'
    if (!password) clientErrors.password = 'Informe sua senha.'
    setErrors(clientErrors)
    if (Object.keys(clientErrors).length > 0) return

    login.mutate(
      { email: email.trim(), password },
      {
        onSuccess: () => navigate('/', { replace: true }),
        onError: (err) => {
          if (
            err instanceof ApiError &&
            err.code === 'VALIDATION_ERROR' &&
            Object.keys(err.fields).length > 0
          ) {
            setErrors(err.fields)
          } else {
            setFormError(messageForError(err))
          }
        },
      },
    )
  }

  return (
    <AuthCard title="Entrar">
      <form noValidate onSubmit={onSubmit} className="flex flex-col gap-4" aria-label="Entrar">
        {formError ? (
          <Alert variant="destructive">
            <AlertDescription>{formError}</AlertDescription>
          </Alert>
        ) : null}
        <FormField
          id="login-email"
          label="E-mail"
          type="email"
          autoComplete="email"
          value={email}
          error={errors.email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <FormField
          id="login-password"
          label="Senha"
          type="password"
          autoComplete="current-password"
          value={password}
          error={errors.password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <Button type="submit" disabled={login.isPending}>
          {login.isPending ? 'Entrando…' : 'Entrar'}
        </Button>
      </form>
      <div className="flex flex-col gap-1 text-sm">
        <Link to="/esqueci-senha" className="text-primary underline-offset-4 hover:underline">
          Esqueci minha senha
        </Link>
        <p className="text-muted-foreground">
          Ainda não tem conta?{' '}
          <Link to="/cadastro" className="text-primary underline-offset-4 hover:underline">
            Criar conta
          </Link>
        </p>
      </div>
    </AuthCard>
  )
}
