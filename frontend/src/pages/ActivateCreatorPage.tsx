import { Navigate, useNavigate } from 'react-router'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { ApiError } from '@/lib/api'
import { AuthCard } from '@/features/auth/components/FormField'
import { useMe } from '@/features/auth/hooks'
import { CreatorProfileForm } from '@/features/creator/components/CreatorProfileForm'
import { useActivateCreatorProfile, useMyCreatorProfile } from '@/features/creator/hooks'

export function ActivateCreatorPage() {
  const navigate = useNavigate()
  const { data: me, isPending: meLoading } = useMe()
  const { data: profile, isPending: profileLoading } = useMyCreatorProfile({ enabled: !!me })
  const activate = useActivateCreatorProfile()

  if (meLoading) return <p role="status">Carregando…</p>
  if (!me) return <Navigate to="/entrar" replace />
  if (profileLoading) return <p role="status">Carregando…</p>
  // Not while a submit is in flight/just succeeded: onSuccess navigates to the public page.
  if (profile && !activate.isSuccess) return <Navigate to="/criador/editar" replace />

  return (
    <AuthCard
      title="Ativar perfil de criador"
      description="Escolha seu nome de usuário e como você aparece para os assinantes."
    >
      {!me.emailVerified ? (
        <Alert>
          <AlertDescription>Confirme seu e-mail para ativar o perfil de criador.</AlertDescription>
        </Alert>
      ) : null}
      <CreatorProfileForm
        submitLabel="Ativar perfil"
        disabled={!me.emailVerified}
        isPending={activate.isPending}
        serverError={activate.error instanceof ApiError ? activate.error : null}
        onSubmit={(values) =>
          activate.mutate(values, { onSuccess: (p) => navigate(`/c/${p.handle}`) })
        }
      />
    </AuthCard>
  )
}
