import { Link, useParams } from 'react-router'
import { Button } from '@/components/ui/button'
import { useMe } from '@/features/auth/hooks'
import { useMyCreatorProfile, usePublicCreator } from '@/features/creator/hooks'
import { PublicVideoList } from '@/features/video/components/PublicVideoList'
import { usePublicVideos } from '@/features/video/hooks'

export function CreatorPublicPage() {
  const { handle = '' } = useParams()
  const { data: creator, isPending, isError } = usePublicCreator(handle)
  const { data: me } = useMe()
  const { data: myProfile } = useMyCreatorProfile({ enabled: !!me })
  const videos = usePublicVideos(handle, { enabled: !!creator })

  if (isPending) return <p role="status">Carregando…</p>

  if (isError) {
    return (
      <p role="alert" className="text-destructive">
        Não foi possível carregar o perfil. Tente novamente mais tarde.
      </p>
    )
  }

  if (!creator) {
    return (
      <section className="flex flex-col items-start gap-3">
        <h1 className="text-2xl font-bold tracking-tight">Criador não encontrado</h1>
        <p className="text-muted-foreground">Este perfil não existe ou foi alterado.</p>
        <Link to="/" className="text-primary underline-offset-4 hover:underline">
          Voltar para o início
        </Link>
      </section>
    )
  }

  const isOwner = !!myProfile && myProfile.handle === creator.handle

  return (
    <article className="flex flex-col gap-8">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h1 className="text-3xl font-bold tracking-tight">{creator.displayName}</h1>
          <p className="text-muted-foreground">@{creator.handle}</p>
        </div>
        <div className="flex flex-col items-end gap-2">
          <div className="flex items-center gap-2">
            {isOwner ? (
              <Button asChild variant="outline" size="sm">
                <Link to="/criador/editar">Editar perfil</Link>
              </Button>
            ) : null}
            <Button type="button" disabled>
              Assinar
            </Button>
          </div>
          <p className="text-muted-foreground text-xs">Em breve</p>
        </div>
      </header>
      <p>{creator.bio ?? <span className="text-muted-foreground">Este criador ainda não escreveu uma bio.</span>}</p>
      <section aria-labelledby="creator-videos-heading" className="flex flex-col gap-3">
        <h2 id="creator-videos-heading" className="text-xl font-semibold">
          Vídeos
        </h2>
        {videos.isPending ? (
          <p role="status">Carregando vídeos…</p>
        ) : videos.isError ? (
          <p role="alert" className="text-destructive">
            Não foi possível carregar os vídeos. Tente novamente mais tarde.
          </p>
        ) : (
          <PublicVideoList videos={videos.data ?? []} />
        )}
      </section>
    </article>
  )
}
