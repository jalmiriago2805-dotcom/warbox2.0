import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { AuthCard, FormField } from '@/features/auth/components/FormField'
import { messageForError } from '@/features/auth/errorMessages'
import { useRequestPasswordReset } from '@/features/auth/hooks'
import { validateEmail } from '@/features/auth/validation'

const NEUTRAL_RESET_MESSAGE =
  'Se existir uma conta com esse e-mail, enviaremos um link para redefinir sua senha.'

export function ForgotPasswordPage() {
  const request = useRequestPasswordReset()
  const [email, setEmail] = useState('')
  const [emailError, setEmailError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)

  const onSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setFormError(null)
    const error = validateEmail(email)
    setEmailError(error)
    if (error) return
    request.mutate(email.trim(), {
      onError: (err) => {
        if (err instanceof ApiError && err.fields.email) setEmailError(err.fields.email)
        else setFormError(messageForError(err))
      },
    })
  }

  if (request.isSuccess) {
    return (
      <AuthCard title="Verifique seu e-mail">
        <p role="status">{NEUTRAL_RESET_MESSAGE}</p>
        <Link to="/entrar" className="text-primary text-sm underline-offset-4 hover:underline">
          Voltar para o login
        </Link>
      </AuthCard>
    )
  }

  return (
    <AuthCard
      title="Esqueci minha senha"
      description="Informe o e-mail da sua conta e enviaremos um link para criar uma nova senha."
    >
      <form noValidate onSubmit={onSubmit} className="flex flex-col gap-4" aria-label="Esqueci minha senha">
        {formError ? (
          <Alert variant="destructive">
            <AlertDescription>{formError}</AlertDescription>
          </Alert>
        ) : null}
        <FormField
          id="forgot-email"
          label="E-mail"
          type="email"
          autoComplete="email"
          value={email}
          error={emailError}
          onChange={(e) => {
            setEmail(e.target.value)
            setEmailError(null)
          }}
        />
        <Button type="submit" disabled={request.isPending}>
          {request.isPending ? 'Enviando…' : 'Enviar link'}
        </Button>
      </form>
      <Link to="/entrar" className="text-primary text-sm underline-offset-4 hover:underline">
        Voltar para o login
      </Link>
    </AuthCard>
  )
}
