import { Link, Navigate, useNavigate, useParams } from 'react-router'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { useMe } from '@/features/auth/hooks'
import { useMyCreatorProfile } from '@/features/creator/hooks'
import { DeleteVideoButton } from '@/features/video/components/DeleteVideoButton'
import { VideoMetadataForm } from '@/features/video/components/VideoMetadataForm'
import {
  VideoStatusBadge,
  VideoVisibilityBadge,
} from '@/features/video/components/VideoStatusBadge'
import { errorMessage, formatDuration } from '@/features/video/format'
import {
  useDeleteVideo,
  useMyVideo,
  usePublishVideo,
  useUnpublishVideo,
  useUpdateVideo,
} from '@/features/video/hooks'

const PLAYER_ALLOW = 'accelerometer; gyroscope; autoplay; encrypted-media; picture-in-picture'

export function EditVideoPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const { data: me, isPending: meLoading } = useMe()
  const { data: profile, isPending: profileLoading } = useMyCreatorProfile({ enabled: !!me })
  const { data: video, isPending: videoLoading, isError } = useMyVideo(id, { enabled: !!profile })
  const update = useUpdateVideo(id)
  const publish = usePublishVideo()
  const unpublish = useUnpublishVideo()
  const remove = useDeleteVideo()

  if (meLoading) return <p role="status">Carregando…</p>
  if (!me) return <Navigate to="/entrar" replace />
  if (profileLoading) return <p role="status">Carregando…</p>
  if (!profile) return <Navigate to="/criador/ativar" replace />
  if (remove.isSuccess) return <p role="status">Vídeo excluído. Redirecionando…</p>
  if (videoLoading) return <p role="status">Carregando…</p>
  if (isError) {
    return (
      <p role="alert" className="text-destructive">
        Não foi possível carregar o vídeo. Tente novamente mais tarde.
      </p>
    )
  }

  if (!video) {
    return (
      <section className="flex flex-col items-start gap-3">
        <h1 className="text-2xl font-bold tracking-tight">Vídeo não encontrado</h1>
        <p className="text-muted-foreground">Este vídeo não existe ou não pertence à sua conta.</p>
        <Link to="/criador/videos" className="text-primary underline-offset-4 hover:underline">
          Voltar para Meus vídeos
        </Link>
      </section>
    )
  }

  const published = video.visibility === 'PUBLISHED'
  const toggling = publish.isPending || unpublish.isPending
  const actionError = publish.error ?? unpublish.error ?? remove.error
  const updateError = update.error instanceof ApiError ? update.error : null
  const updateFields = updateError?.code === 'VALIDATION_ERROR' ? updateError.fields : {}
  const updateMessage = update.error
    ? updateError?.code === 'VALIDATION_ERROR' && Object.keys(updateFields).length > 0
      ? null
      : errorMessage(update.error)
    : null

  return (
    <section className="mx-auto flex w-full max-w-2xl flex-col gap-6" aria-labelledby="edit-video-heading">
      <header className="flex flex-col gap-2">
        <Link to="/criador/videos" className="text-primary text-sm underline-offset-4 hover:underline">
          ← Meus vídeos
        </Link>
        <h1 id="edit-video-heading" className="text-2xl font-bold tracking-tight">
          Editar vídeo
        </h1>
      </header>

      <div className="flex flex-col gap-2" aria-label="Situação do vídeo" role="group">
        <div className="flex flex-wrap items-center gap-2">
          <VideoStatusBadge status={video.processingStatus} />
          <VideoVisibilityBadge visibility={video.visibility} />
          <span className="text-muted-foreground text-sm">
            Duração: {formatDuration(video.durationSeconds)}
          </span>
        </div>
        {video.processingStatus === 'PENDING_UPLOAD' || video.processingStatus === 'PROCESSING' ? (
          <p role="status" className="text-muted-foreground text-sm">
            O vídeo ainda está sendo processado. Esta página atualiza sozinha.
          </p>
        ) : null}
        {video.processingStatus === 'ERROR' ? (
          <p role="alert" className="text-destructive text-sm">
            Houve um erro ao processar o vídeo. Exclua-o e envie novamente.
          </p>
        ) : null}
      </div>

      {video.playerUrl ? (
        <div className="bg-muted relative aspect-video overflow-hidden rounded-lg">
          <iframe
            src={video.playerUrl}
            title={`Prévia: ${video.title}`}
            allow={PLAYER_ALLOW}
            allowFullScreen
            className="absolute inset-0 h-full w-full border-0"
          />
        </div>
      ) : null}

      {updateMessage ? (
        <Alert variant="destructive">
          <AlertDescription>{updateMessage}</AlertDescription>
        </Alert>
      ) : null}
      {update.isSuccess ? (
        <Alert role="status">
          <AlertDescription>Alterações salvas.</AlertDescription>
        </Alert>
      ) : null}

      <VideoMetadataForm
        key={video.id}
        initial={{ title: video.title, description: video.description, accessType: video.accessType }}
        submitLabel="Salvar alterações"
        submitting={update.isPending}
        serverFields={updateFields}
        onSubmit={(values) => {
          update.reset()
          update.mutate(values)
        }}
      />

      {actionError ? (
        <Alert variant="destructive">
          <AlertDescription>{errorMessage(actionError)}</AlertDescription>
        </Alert>
      ) : null}

      <div className="flex flex-wrap items-center gap-3 border-t pt-4">
        {published ? (
          <Button
            type="button"
            variant="outline"
            disabled={toggling}
            onClick={() => {
              publish.reset()
              remove.reset()
              unpublish.mutate(video.id)
            }}
          >
            {unpublish.isPending ? 'Despublicando…' : 'Despublicar'}
          </Button>
        ) : (
          <Button
            type="button"
            disabled={toggling}
            onClick={() => {
              unpublish.reset()
              remove.reset()
              publish.mutate(video.id)
            }}
          >
            {publish.isPending ? 'Publicando…' : 'Publicar'}
          </Button>
        )}
        <DeleteVideoButton
          title={video.title}
          pending={remove.isPending}
          onConfirm={() => {
            publish.reset()
            unpublish.reset()
            remove.mutate(video.id, { onSuccess: () => navigate('/criador/videos') })
          }}
        />
      </div>
    </section>
  )
}
