import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { AuthCard, FormField } from '@/features/auth/components/FormField'
import { messageForError } from '@/features/auth/errorMessages'
import { useConfirmPasswordReset } from '@/features/auth/hooks'
import { validatePassword, validatePasswordConfirmation } from '@/features/auth/validation'

type FieldErrors = { newPassword?: string; confirmation?: string }

function InvalidLink() {
  return (
    <AuthCard title="Link inválido">
      <Alert variant="destructive">
        <AlertDescription>Este link é inválido ou expirou.</AlertDescription>
      </Alert>
      <Link to="/esqueci-senha" className="text-primary text-sm underline-offset-4 hover:underline">
        Pedir um novo link
      </Link>
    </AuthCard>
  )
}

export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token')?.trim() || null
  const confirm = useConfirmPasswordReset()
  const [newPassword, setNewPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [tokenInvalid, setTokenInvalid] = useState(false)

  if (!token || tokenInvalid) return <InvalidLink />

  if (confirm.isSuccess) {
    return (
      <AuthCard title="Senha redefinida!">
        <p role="status">Sua nova senha já está valendo.</p>
        <Button asChild>
          <Link to="/entrar">Entrar</Link>
        </Button>
      </AuthCard>
    )
  }

  const onSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setFormError(null)
    const clientErrors: FieldErrors = {}
    const pw = validatePassword(newPassword)
    if (pw) clientErrors.newPassword = pw
    const conf = validatePasswordConfirmation(newPassword, confirmation)
    if (conf) clientErrors.confirmation = conf
    setErrors(clientErrors)
    if (Object.keys(clientErrors).length > 0) return

    confirm.mutate(
      { token, newPassword },
      {
        onError: (err) => {
          if (err instanceof ApiError && err.code === 'INVALID_OR_EXPIRED_TOKEN') {
            setTokenInvalid(true)
          } else if (err instanceof ApiError && err.fields.newPassword) {
            setErrors({ newPassword: err.fields.newPassword })
          } else {
            setFormError(messageForError(err))
          }
        },
      },
    )
  }

  return (
    <AuthCard title="Redefinir senha" description="Escolha uma nova senha para sua conta.">
      <form noValidate onSubmit={onSubmit} className="flex flex-col gap-4" aria-label="Redefinir senha">
        {formError ? (
          <Alert variant="destructive">
            <AlertDescription>{formError}</AlertDescription>
          </Alert>
        ) : null}
        <FormField
          id="reset-new-password"
          label="Nova senha"
          type="password"
          autoComplete="new-password"
          value={newPassword}
          error={errors.newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
        />
        <FormField
          id="reset-confirmation"
          label="Confirme a nova senha"
          type="password"
          autoComplete="new-password"
          value={confirmation}
          error={errors.confirmation}
          onChange={(e) => setConfirmation(e.target.value)}
        />
        <Button type="submit" disabled={confirm.isPending}>
          {confirm.isPending ? 'Salvando…' : 'Redefinir senha'}
        </Button>
      </form>
    </AuthCard>
  )
}
