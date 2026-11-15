import { Link, Navigate } from 'react-router'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { ApiError } from '@/lib/api'
import { AuthCard } from '@/features/auth/components/FormField'
import { useMe } from '@/features/auth/hooks'
import { CreatorProfileForm } from '@/features/creator/components/CreatorProfileForm'
import { useMyCreatorProfile, useUpdateCreatorProfile } from '@/features/creator/hooks'

export function EditCreatorProfilePage() {
  const { data: me, isPending: meLoading } = useMe()
  const { data: profile, isPending: profileLoading, isError } = useMyCreatorProfile({ enabled: !!me })
  const update = useUpdateCreatorProfile()

  if (meLoading) return <p role="status">Carregando…</p>
  if (!me) return <Navigate to="/entrar" replace />
  if (profileLoading) return <p role="status">Carregando…</p>
  if (isError) {
    return (
      <p role="alert" className="text-destructive">
        Não foi possível carregar seu perfil. Tente novamente mais tarde.
      </p>
    )
  }
  if (!profile) return <Navigate to="/criador/ativar" replace />

  const saved = update.isSuccess ? update.data : null

  return (
    <AuthCard title="Editar perfil de criador">
      {saved ? (
        <Alert role="status">
          <AlertDescription>
            <span>
              Perfil atualizado.{' '}
              <Link to={`/c/${saved.handle}`} className="text-primary underline-offset-4 hover:underline">
                Ver página pública
              </Link>
            </span>
          </AlertDescription>
        </Alert>
      ) : null}
      <CreatorProfileForm
        initialValues={{ handle: profile.handle, displayName: profile.displayName, bio: profile.bio }}
        submitLabel="Salvar alterações"
        isPending={update.isPending}
        serverError={update.error instanceof ApiError ? update.error : null}
        onSubmit={(values) => update.mutate(values)}
      />
    </AuthCard>
  )
}
