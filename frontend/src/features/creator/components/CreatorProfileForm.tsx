import { useState, type FormEvent } from 'react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import type { ApiError } from '@/lib/api'
import { FormField } from '@/features/auth/components/FormField'
import type { CreatorProfileRequest } from '../api'
import { BIO_MAX, normalizeHandle, validateCreatorProfile, type CreatorFieldErrors } from '../validation'

type Props = {
  initialValues?: CreatorProfileRequest
  submitLabel: string
  disabled?: boolean
  isPending: boolean
  serverError?: ApiError | null
  onSubmit: (values: CreatorProfileRequest) => void
}

export function CreatorProfileForm({
  initialValues,
  submitLabel,
  disabled,
  isPending,
  serverError,
  onSubmit,
}: Props) {
  const [handle, setHandle] = useState(initialValues?.handle ?? '')
  const [displayName, setDisplayName] = useState(initialValues?.displayName ?? '')
  const [bio, setBio] = useState(initialValues?.bio ?? '')
  const [clientErrors, setClientErrors] = useState<CreatorFieldErrors>({})

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    const values: CreatorProfileRequest = {
      handle: normalizeHandle(handle),
      displayName: displayName.trim(),
      bio: bio.trim() || null,
    }
    const errors = validateCreatorProfile(values)
    setClientErrors(errors)
    if (Object.keys(errors).length > 0) return
    onSubmit(values)
  }

  const serverFields: CreatorFieldErrors = {}
  let alertMessage: string | null = null
  if (serverError) {
    if (serverError.code === 'HANDLE_TAKEN') {
      serverFields.handle = serverError.fields.handle ?? serverError.message
    } else if (serverError.code === 'VALIDATION_ERROR' && Object.keys(serverError.fields).length > 0) {
      Object.assign(serverFields, serverError.fields)
    } else {
      alertMessage = serverError.message
    }
  }
  const errors = { ...serverFields, ...clientErrors }
  const locked = disabled || isPending

  return (
    <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4" aria-label="Perfil de criador">
      {alertMessage ? (
        <Alert variant="destructive">
          <AlertDescription>{alertMessage}</AlertDescription>
        </Alert>
      ) : null}
      <div className="flex flex-col gap-1">
        <FormField
          id="creator-handle"
          label="Nome de usuário"
          autoComplete="off"
          autoCapitalize="none"
          value={handle}
          error={errors.handle}
          disabled={locked}
          onChange={(e) => setHandle(e.target.value.toLowerCase())}
        />
        <p className="text-muted-foreground text-sm">
          Seu perfil: /c/{normalizeHandle(handle) || 'seu-nome'}
        </p>
      </div>
      <FormField
        id="creator-display-name"
        label="Nome de exibição"
        value={displayName}
        error={errors.displayName}
        disabled={locked}
        onChange={(e) => setDisplayName(e.target.value)}
      />
      <div className="flex flex-col gap-2">
        <Label htmlFor="creator-bio">Bio</Label>
        <Textarea
          id="creator-bio"
          value={bio}
          rows={4}
          disabled={locked}
          aria-invalid={errors.bio ? true : undefined}
          aria-describedby={errors.bio ? 'creator-bio-error' : undefined}
          onChange={(e) => setBio(e.target.value)}
        />
        <p className="text-muted-foreground text-right text-xs">
          {bio.length}/{BIO_MAX}
        </p>
        {errors.bio ? (
          <p id="creator-bio-error" className="text-destructive text-sm">
            {errors.bio}
          </p>
        ) : null}
      </div>
      <Button type="submit" disabled={locked}>
        {isPending ? 'Salvando…' : submitLabel}
      </Button>
    </form>
  )
}
