import { useState } from 'react'
import { Button } from '@/components/ui/button'

type Props = {
  title: string
  pending: boolean
  onConfirm: () => void
}

/** "Excluir" button that asks for an inline confirmation before calling `onConfirm`. */
export function DeleteVideoButton({ title, pending, onConfirm }: Props) {
  const [confirming, setConfirming] = useState(false)

  if (!confirming) {
    return (
      <Button
        type="button"
        variant="outline"
        size="sm"
        aria-label={`Excluir ${title}`}
        onClick={() => setConfirming(true)}
      >
        Excluir
      </Button>
    )
  }

  return (
    <div role="group" aria-label={`Confirmar exclusão de ${title}`} className="flex flex-wrap items-center gap-2">
      <span className="text-sm">Excluir este vídeo definitivamente?</span>
      <Button type="button" variant="destructive" size="sm" disabled={pending} onClick={onConfirm}>
        {pending ? 'Excluindo…' : 'Confirmar exclusão'}
      </Button>
      <Button type="button" variant="ghost" size="sm" disabled={pending} onClick={() => setConfirming(false)}>
        Cancelar
      </Button>
    </div>
  )
}
