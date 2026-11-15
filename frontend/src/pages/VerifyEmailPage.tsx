import { useEffect, useRef } from 'react'
import { Link, useSearchParams } from 'react-router'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { AuthCard } from '@/features/auth/components/FormField'
import { ResendVerificationButton } from '@/features/auth/components/EmailVerificationBanner'
import { messageForError } from '@/features/auth/errorMessages'
import { useMe, useVerifyEmail } from '@/features/auth/hooks'

const INVALID_LINK_MESSAGE = 'Este link é inválido ou expirou.'

export function VerifyEmailPage() {
  const [params] = useSearchParams()
  const token = params.get('token')?.trim() || null
  const verify = useVerifyEmail()
  const { data: me } = useMe()
  const { mutate } = verify
  // Tokens are single-use: guard against StrictMode's double effect run.
  const started = useRef(false)

  useEffect(() => {
    if (!token || started.current) return
    started.current = true
    mutate(token)
  }, [token, mutate])

  if (verify.isSuccess) {
    return (
      <AuthCard title="E-mail confirmado!">
        <p role="status">Tudo certo. Agora você pode aproveitar tudo no Warbox.</p>
        <Button asChild>
          <Link to="/">Ir para o início</Link>
        </Button>
      </AuthCard>
    )
  }

  if (!token || verify.isError) {
    const message = verify.isError ? messageForError(verify.error) : INVALID_LINK_MESSAGE
    const canResend = me != null && !me.emailVerified
    return (
      <AuthCard title="Não foi possível confirmar seu e-mail">
        <Alert variant="destructive">
          <AlertTitle>Falha na confirmação</AlertTitle>
          <AlertDescription>{message}</AlertDescription>
        </Alert>
        {canResend ? (
          <>
            <p className="text-sm">Peça um novo link de confirmação:</p>
            <ResendVerificationButton />
          </>
        ) : (
          <p className="text-muted-foreground text-sm">
            {me ? (
              <Link to="/" className="text-primary underline-offset-4 hover:underline">
                Ir para o início
              </Link>
            ) : (
              <>
                <Link to="/entrar" className="text-primary underline-offset-4 hover:underline">
                  Entre na sua conta
                </Link>{' '}
                para pedir um novo link.
              </>
            )}
          </p>
        )}
      </AuthCard>
    )
  }

  return (
    <AuthCard title="Confirmando e-mail">
      <p role="status" aria-live="polite">
        Verificando seu e-mail…
      </p>
    </AuthCard>
  )
}
