import { Badge } from '@/components/ui/badge'
import type { VideoAccessType, VideoProcessingStatus, VideoVisibility } from '../api'

const STATUS: Record<
  VideoProcessingStatus,
  { label: string; variant: 'secondary' | 'default' | 'destructive' | 'outline' }
> = {
  PENDING_UPLOAD: { label: 'Aguardando envio', variant: 'outline' },
  PROCESSING: { label: 'Processando', variant: 'secondary' },
  READY: { label: 'Pronto', variant: 'default' },
  ERROR: { label: 'Erro', variant: 'destructive' },
}

export function processingStatusLabel(status: VideoProcessingStatus): string {
  return STATUS[status].label
}

export function VideoStatusBadge({ status }: { status: VideoProcessingStatus }) {
  const { label, variant } = STATUS[status]
  return <Badge variant={variant}>{label}</Badge>
}

export function VideoVisibilityBadge({ visibility }: { visibility: VideoVisibility }) {
  return visibility === 'PUBLISHED' ? (
    <Badge variant="default">Publicado</Badge>
  ) : (
    <Badge variant="outline">Rascunho</Badge>
  )
}

export function VideoAccessBadge({ accessType }: { accessType: VideoAccessType }) {
  return <Badge variant="secondary">{accessType === 'FREE' ? 'Gratuito' : 'Exclusivo'}</Badge>
}
