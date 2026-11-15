import { Lock } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import type { PublicVideo } from '../api'
import { formatDuration } from '../format'

const PLAYER_ALLOW = 'accelerometer; gyroscope; autoplay; encrypted-media; picture-in-picture'

function VideoMeta({ video }: { video: PublicVideo }) {
  return (
    <div className="flex flex-col gap-1">
      <h3 className="font-medium">{video.title}</h3>
      <p className="text-muted-foreground text-xs">{formatDuration(video.durationSeconds)}</p>
      {video.description ? <p className="text-muted-foreground text-sm">{video.description}</p> : null}
    </div>
  )
}

function FreeVideoCard({ video }: { video: PublicVideo }) {
  return (
    <Card className="overflow-hidden py-0">
      <div className="bg-muted relative aspect-video">
        {video.playerUrl ? (
          <iframe
            src={video.playerUrl}
            title={video.title}
            allow={PLAYER_ALLOW}
            allowFullScreen
            loading="lazy"
            className="absolute inset-0 h-full w-full border-0"
          />
        ) : video.thumbnailUrl ? (
          <img src={video.thumbnailUrl} alt="" className="h-full w-full object-cover" loading="lazy" />
        ) : null}
      </div>
      <CardContent className="pb-4">
        <VideoMeta video={video} />
      </CardContent>
    </Card>
  )
}

function LockedVideoCard({ video }: { video: PublicVideo }) {
  return (
    <Card className="overflow-hidden py-0">
      <div className="bg-muted text-muted-foreground flex aspect-video flex-col items-center justify-center gap-2">
        <Lock className="size-8" aria-hidden="true" />
        <p className="text-sm font-medium">Exclusivo para assinantes</p>
      </div>
      <CardContent className="pb-4">
        <VideoMeta video={video} />
      </CardContent>
    </Card>
  )
}

export function PublicVideoList({ videos }: { videos: PublicVideo[] }) {
  if (videos.length === 0) {
    return <p className="text-muted-foreground">Este criador ainda não publicou vídeos.</p>
  }
  return (
    <ul className="grid gap-4 sm:grid-cols-2">
      {videos.map((video) => (
        <li key={video.id}>{video.locked ? <LockedVideoCard video={video} /> : <FreeVideoCard video={video} />}</li>
      ))}
    </ul>
  )
}
