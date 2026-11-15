import { Upload } from 'tus-js-client'

export const TUS_CHUNK_SIZE = 50 * 1024 * 1024
export const TUS_RETRY_DELAYS = [0, 3000, 10000]

export type StreamUpload = {
  promise: Promise<void>
  /** Stops the upload; `promise` then rejects with an AbortError. */
  abort: () => void
}

export function createAbortError(): Error {
  const err = new Error('Upload cancelled')
  err.name = 'AbortError'
  return err
}

export function isAbortError(err: unknown): boolean {
  return err instanceof Error && err.name === 'AbortError'
}

/**
 * Uploads `file` straight to Cloudflare Stream over tus, using the URL created by the backend.
 * This is the only place the browser talks to something other than our API.
 */
export function uploadToStream(
  file: File,
  uploadUrl: string,
  onProgress: (pct: number) => void,
): StreamUpload {
  let upload: Upload | undefined
  let rejectUpload: (reason: unknown) => void = () => undefined
  const promise = new Promise<void>((resolve, reject) => {
    rejectUpload = reject
    upload = new Upload(file, {
      uploadUrl,
      chunkSize: TUS_CHUNK_SIZE,
      retryDelays: TUS_RETRY_DELAYS,
      onProgress: (sent, total) => onProgress(total > 0 ? Math.floor((sent / total) * 100) : 0),
      onSuccess: () => resolve(),
      onError: (error) => reject(error),
    })
    upload.start()
  })
  return {
    promise,
    abort: () => {
      // tus-js-client fires no callback on abort, so settle the promise ourselves.
      rejectUpload(createAbortError())
      void Promise.resolve(upload?.abort()).catch(() => undefined)
    },
  }
}
