import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { messageForError } from '../errorMessages'
import { useMe, useResendVerification } from '../hooks'

/** "Reenviar e-mail" button with inline success / error feedback. */
export function ResendVerificationButton() {
  const resend = useResendVerification()

  let feedback: string | null = null
  if (resend.isSuccess) feedback = 'E-mail reenviado.'
  else if (resend.error instanceof ApiError && resend.error.status === 429) {
    feedback = 'Aguarde um minuto antes de pedir outro e-mail.'
  } else if (resend.isError) feedback = messageForError(resend.error)

  return (
    <div className="flex flex-wrap items-center gap-3">
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={resend.isPending}
        onClick={() => resend.mutate()}
      >
        {resend.isPending ? 'Reenviando…' : 'Reenviar e-mail'}
      </Button>
      <p role="status" aria-live="polite" className="text-sm">
        {feedback}
      </p>
    </div>
  )
}

export function EmailVerificationBanner() {
  const { data: me } = useMe()
  if (!me || me.emailVerified) return null

  return (
    <div className="mx-auto max-w-5xl px-4 pt-4">
      <Alert>
        <AlertDescription className="text-foreground">
          <p>
            Confirme seu e-mail para aproveitar tudo no Warbox. Enviamos um link para {me.email}.
          </p>
          <ResendVerificationButton />
        </AlertDescription>
      </Alert>
    </div>
  )
}
