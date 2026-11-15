import { useState, type FormEvent, type ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { FormField } from '@/features/auth/components/FormField'
import type { UpdateVideoRequest, VideoAccessType } from '../api'
import { DESCRIPTION_MAX, TITLE_MAX, validateVideoMetadata } from '../validation'

export type VideoMetadataInitial = {
  title: string
  description: string | null
  accessType: VideoAccessType
}

type Props = {
  initial?: VideoMetadataInitial
  onSubmit: (values: UpdateVideoRequest) => void
  submitting: boolean
  /** Field errors returned by the server (VALIDATION_ERROR). */
  serverFields?: Record<string, string>
  submitLabel: string
  /** Called on submit after the metadata is valid; return false to block the submit. */
  onValidate?: () => boolean
  /** Extra controls (e.g. the file input) rendered right above the submit button. */
  children?: ReactNode
}

const ACCESS_OPTIONS: { value: VideoAccessType; label: string; hint: string }[] = [
  { value: 'FREE', label: 'Gratuito', hint: 'Qualquer pessoa pode assistir.' },
  { value: 'EXCLUSIVE', label: 'Exclusivo', hint: 'Somente para assinantes.' },
]

export function VideoMetadataForm({
  initial,
  onSubmit,
  submitting,
  serverFields = {},
  submitLabel,
  onValidate,
  children,
}: Props) {
  const [title, setTitle] = useState(initial?.title ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [accessType, setAccessType] = useState<VideoAccessType>(initial?.accessType ?? 'FREE')
  const [clientErrors, setClientErrors] = useState<Record<string, string>>({})

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    const values: UpdateVideoRequest = {
      title: title.trim(),
      description: description.trim() || null,
      accessType,
    }
    const errors = validateVideoMetadata(values)
    setClientErrors(errors)
    const extraOk = onValidate ? onValidate() : true
    if (Object.keys(errors).length > 0 || !extraOk) return
    onSubmit(values)
  }

  const errors = { ...serverFields, ...clientErrors }

  return (
    <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4" aria-label="Dados do vídeo">
      <FormField
        id="video-title"
        label="Título"
        value={title}
        maxLength={TITLE_MAX + 50}
        error={errors.title}
        disabled={submitting}
        onChange={(e) => setTitle(e.target.value)}
      />
      <div className="flex flex-col gap-2">
        <Label htmlFor="video-description">Descrição</Label>
        <Textarea
          id="video-description"
          value={description}
          rows={4}
          disabled={submitting}
          aria-invalid={errors.description ? true : undefined}
          aria-describedby={errors.description ? 'video-description-error' : undefined}
          onChange={(e) => setDescription(e.target.value)}
        />
        <p className="text-muted-foreground text-right text-xs">
          {description.length}/{DESCRIPTION_MAX}
        </p>
        {errors.description ? (
          <p id="video-description-error" className="text-destructive text-sm">
            {errors.description}
          </p>
        ) : null}
      </div>
      <fieldset className="flex flex-col gap-2" disabled={submitting}>
        <legend className="mb-1 text-sm font-medium">Tipo de acesso</legend>
        {ACCESS_OPTIONS.map((option) => {
          const id = `video-access-${option.value.toLowerCase()}`
          return (
            <div key={option.value} className="flex items-start gap-2">
              <input
                type="radio"
                id={id}
                name="accessType"
                value={option.value}
                checked={accessType === option.value}
                onChange={() => setAccessType(option.value)}
                className="mt-1"
              />
              <Label htmlFor={id} className="flex flex-col items-start gap-0">
                <span>{option.label}</span>
                <span className="text-muted-foreground text-xs font-normal">{option.hint}</span>
              </Label>
            </div>
          )
        })}
        {errors.accessType ? <p className="text-destructive text-sm">{errors.accessType}</p> : null}
      </fieldset>
      {children}
      <Button type="submit" disabled={submitting}>
        {submitting ? 'Salvando…' : submitLabel}
      </Button>
    </form>
  )
}
