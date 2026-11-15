import { Link, Navigate } from 'react-router'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { useMe } from '@/features/auth/hooks'
import { useMyCreatorProfile } from '@/features/creator/hooks'
import { isFinalStatus, type CreatorVideo } from '@/features/video/api'
import { DeleteVideoButton } from '@/features/video/components/DeleteVideoButton'
import {
  VideoAccessBadge,
  VideoStatusBadge,
  VideoVisibilityBadge,
} from '@/features/video/components/VideoStatusBadge'
import { errorMessage, formatDuration } from '@/features/video/format'
import {
  useDeleteVideo,
  useMyVideos,
  usePublishVideo,
  useUnpublishVideo,
} from '@/features/video/hooks'

function VideoRow({
  video,
  busy,
  deleting,
  onPublish,
  onUnpublish,
  onDelete,
}: {
  video: CreatorVideo
  busy: boolean
  deleting: boolean
  onPublish: () => void
  onUnpublish: () => void
  onDelete: () => void
}) {
  const published = video.visibility === 'PUBLISHED'
  return (
    <li>
      <Card>
        <CardContent className="flex flex-col gap-3">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <div className="flex flex-col gap-1">
              <h2 className="font-semibold">{video.title}</h2>
              <p className="text-muted-foreground text-xs">Duração: {formatDuration(video.durationSeconds)}</p>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <VideoStatusBadge status={video.processingStatus} />
              <VideoVisibilityBadge visibility={video.visibility} />
              <VideoAccessBadge accessType={video.accessType} />
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Button asChild variant="outline" size="sm">
              <Link to={`/criador/videos/${video.id}`} aria-label={`Editar ${video.title}`}>
                Editar
              </Link>
            </Button>
            {published ? (
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={busy}
                aria-label={`Despublicar ${video.title}`}
                onClick={onUnpublish}
              >
                Despublicar
              </Button>
            ) : (
              <Button
                type="button"
                size="sm"
                disabled={busy || video.processingStatus !== 'READY'}
                aria-label={`Publicar ${video.title}`}
                onClick={onPublish}
              >
                Publicar
              </Button>
            )}
            <DeleteVideoButton title={video.title} pending={deleting} onConfirm={onDelete} />
          </div>
          {!published && !isFinalStatus(video.processingStatus) ? (
            <p className="text-muted-foreground text-xs">
              A publicação fica disponível quando o vídeo terminar de processar.
            </p>
          ) : null}
        </CardContent>
      </Card>
    </li>
  )
}

export function MyVideosPage() {
  const { data: me, isPending: meLoading } = useMe()
  const { data: profile, isPending: profileLoading, isError: profileError } = useMyCreatorProfile({
    enabled: !!me,
  })
  const videos = useMyVideos({ enabled: !!profile })
  const publish = usePublishVideo()
  const unpublish = useUnpublishVideo()
  const remove = useDeleteVideo()

  if (meLoading) return <p role="status">Carregando…</p>
  if (!me) return <Navigate to="/entrar" replace />
  if (profileLoading) return <p role="status">Carregando…</p>
  if (profileError) {
    return (
      <p role="alert" className="text-destructive">
        Não foi possível carregar seu perfil. Tente novamente mais tarde.
      </p>
    )
  }

  if (!profile) {
    return (
      <section className="flex flex-col items-start gap-3">
        <h1 className="text-2xl font-bold tracking-tight">Meus vídeos</h1>
        <p className="text-muted-foreground">Ative seu perfil de criador para enviar vídeos.</p>
        <Button asChild>
          <Link to="/criador/ativar">Ativar perfil de criador</Link>
        </Button>
      </section>
    )
  }

  const actionError = publish.error ?? unpublish.error ?? remove.error
  const busy = publish.isPending || unpublish.isPending || remove.isPending

  return (
    <section className="flex flex-col gap-6" aria-labelledby="my-videos-heading">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <h1 id="my-videos-heading" className="text-2xl font-bold tracking-tight">
          Meus vídeos
        </h1>
        <Button asChild>
          <Link to="/criador/videos/novo">Enviar vídeo</Link>
        </Button>
      </header>

      {actionError ? (
        <Alert variant="destructive">
          <AlertDescription>{errorMessage(actionError)}</AlertDescription>
        </Alert>
      ) : null}

      {videos.isPending ? (
        <p role="status">Carregando vídeos…</p>
      ) : videos.isError ? (
        <p role="alert" className="text-destructive">
          Não foi possível carregar seus vídeos. Tente novamente mais tarde.
        </p>
      ) : videos.data.length === 0 ? (
        <Card>
          <CardContent className="text-muted-foreground">
            Você ainda não enviou nenhum vídeo.
          </CardContent>
        </Card>
      ) : (
        <ul className="flex flex-col gap-3">
          {videos.data.map((video) => (
            <VideoRow
              key={video.id}
              video={video}
              busy={busy}
              deleting={remove.isPending && remove.variables === video.id}
              onPublish={() => {
                unpublish.reset()
                remove.reset()
                publish.mutate(video.id)
              }}
              onUnpublish={() => {
                publish.reset()
                remove.reset()
                unpublish.mutate(video.id)
              }}
              onDelete={() => {
                publish.reset()
                unpublish.reset()
                remove.mutate(video.id)
              }}
            />
          ))}
        </ul>
      )}
    </section>
  )
}
