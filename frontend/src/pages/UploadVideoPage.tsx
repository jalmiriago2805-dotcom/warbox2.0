import { useEffect, useRef, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Progress } from '@/components/ui/progress'
import { ApiError } from '@/lib/api'
import { useMe } from '@/features/auth/hooks'
import { useMyCreatorProfile } from '@/features/creator/hooks'
import type { UpdateVideoRequest } from '@/features/video/api'
import { VideoMetadataForm } from '@/features/video/components/VideoMetadataForm'
import { errorMessage, formatFileSize } from '@/features/video/format'
import { useCreateVideo } from '@/features/video/hooks'
import { isAbortError, uploadToStream, type StreamUpload } from '@/features/video/upload'
import { validateVideoFile } from '@/features/video/validation'

type Phase = 'idle' | 'creating' | 'uploading'

export function UploadVideoPage() {
  const navigate = useNavigate()
  const { data: me, isPending: meLoading } = useMe()
  const { data: profile, isPending: profileLoading } = useMyCreatorProfile({ enabled: !!me })
  const createVideo = useCreateVideo()

  const [file, setFile] = useState<File | null>(null)
  const [fileError, setFileError] = useState<string | null>(null)
  const [phase, setPhase] = useState<Phase>('idle')
  const [progress, setProgress] = useState(0)
  const [serverError, setServerError] = useState<ApiError | null>(null)
  const [uploadError, setUploadError] = useState<string | null>(null)
  const [cancelled, setCancelled] = useState(false)
  const activeUpload = useRef<StreamUpload | null>(null)

  // Leaving the page must stop an upload in flight.
  useEffect(() => () => activeUpload.current?.abort(), [])

  if (meLoading) return <p role="status">Carregando…</p>
  if (!me) return <Navigate to="/entrar" replace />
  if (profileLoading) return <p role="status">Carregando…</p>

  if (!profile) {
    return (
      <section className="flex flex-col items-start gap-3">
        <h1 className="text-2xl font-bold tracking-tight">Enviar vídeo</h1>
        <p className="text-muted-foreground">Ative seu perfil de criador para enviar vídeos.</p>
        <Button asChild>
          <Link to="/criador/ativar">Ativar perfil de criador</Link>
        </Button>
      </section>
    )
  }

  const busy = phase !== 'idle'

  const validateFile = (): boolean => {
    const errors = validateVideoFile(file)
    setFileError(errors.file ?? null)
    return !errors.file
  }

  const submit = async (values: UpdateVideoRequest) => {
    if (!file) return
    setServerError(null)
    setUploadError(null)
    setCancelled(false)
    setProgress(0)
    setPhase('creating')

    let created
    try {
      created = await createVideo.mutateAsync({ ...values, sizeBytes: file.size })
    } catch (err) {
      setServerError(err instanceof ApiError ? err : null)
      if (!(err instanceof ApiError)) setUploadError(errorMessage(err))
      setPhase('idle')
      return
    }

    setPhase('uploading')
    const upload = uploadToStream(file, created.uploadUrl, setProgress)
    activeUpload.current = upload
    try {
      await upload.promise
      activeUpload.current = null
      navigate(`/criador/videos/${created.video.id}`)
    } catch (err) {
      activeUpload.current = null
      if (isAbortError(err)) setCancelled(true)
      else setUploadError('Falha no envio do vídeo. Verifique sua conexão e tente novamente.')
      setPhase('idle')
    }
  }

  const fieldErrors = serverError?.code === 'VALIDATION_ERROR' ? serverError.fields : {}
  const alertMessage =
    serverError && serverError.code !== 'VALIDATION_ERROR' ? errorMessage(serverError) : uploadError
  const sizeError = fieldErrors.sizeBytes
  const shownFileError = fileError ?? sizeError ?? null

  return (
    <section className="mx-auto flex w-full max-w-xl flex-col gap-4" aria-labelledby="upload-video-heading">
      <h1 id="upload-video-heading" className="text-2xl font-bold tracking-tight">
        Enviar vídeo
      </h1>

      {alertMessage ? (
        <Alert variant="destructive">
          <AlertDescription>{alertMessage}</AlertDescription>
        </Alert>
      ) : null}
      {cancelled ? (
        <Alert role="status">
          <AlertDescription>
            Envio cancelado. O vídeo continua como rascunho em{' '}
            <Link to="/criador/videos" className="text-primary underline-offset-4 hover:underline">
              Meus vídeos
            </Link>
            .
          </AlertDescription>
        </Alert>
      ) : null}

      <VideoMetadataForm
        submitLabel="Enviar vídeo"
        submitting={busy}
        serverFields={fieldErrors}
        onValidate={validateFile}
        onSubmit={(values) => void submit(values)}
      >
        <div className="flex flex-col gap-2">
          <Label htmlFor="video-file">Arquivo de vídeo</Label>
          <input
            id="video-file"
            type="file"
            accept="video/*"
            disabled={busy}
            aria-invalid={shownFileError ? true : undefined}
            aria-describedby={shownFileError ? 'video-file-error' : 'video-file-hint'}
            className="text-sm file:mr-3 file:rounded-md file:border file:px-3 file:py-1.5"
            onChange={(e) => {
              setFile(e.target.files?.[0] ?? null)
              setFileError(null)
            }}
          />
          <p id="video-file-hint" className="text-muted-foreground text-xs">
            Vídeos de até 60 minutos e 30 GB.
            {file ? ` Selecionado: ${file.name} (${formatFileSize(file.size)}).` : ''}
          </p>
          {shownFileError ? (
            <p id="video-file-error" className="text-destructive text-sm">
              {shownFileError}
            </p>
          ) : null}
        </div>
      </VideoMetadataForm>

      {phase === 'uploading' ? (
        <div className="flex flex-col gap-2" aria-live="polite">
          <div className="flex items-center justify-between text-sm">
            <span>Enviando…</span>
            <span>{progress}%</span>
          </div>
          <Progress value={progress} aria-label="Progresso do envio" />
          <Button
            type="button"
            variant="outline"
            className="self-start"
            onClick={() => activeUpload.current?.abort()}
          >
            Cancelar envio
          </Button>
        </div>
      ) : null}
    </section>
  )
}
