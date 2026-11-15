import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Upload } from 'tus-js-client'
import { UploadVideoPage } from './UploadVideoPage'
import { renderWithProviders } from '@/test/test-utils'
import { callsTo, errorResponse, jsonResponse, mockFetch, sentJson } from '@/test/fetch-mock'

type TusOptions = {
  uploadUrl?: string
  chunkSize?: number
  onProgress?: (sent: number, total: number) => void
  onSuccess?: () => void
  onError?: (error: Error) => void
}

const tus = vi.hoisted(() => ({
  instances: [] as { file: File; options: TusOptions; start: () => void; abort: () => Promise<void> }[],
}))

vi.mock('tus-js-client', () => ({
  Upload: vi.fn(function (this: unknown, file: File, options: TusOptions) {
    const instance = { file, options, start: vi.fn(), abort: vi.fn(() => Promise.resolve()) }
    tus.instances.push(instance)
    return instance
  }),
}))

const ME = { id: 'u1', name: 'Ana', email: 'ana@example.com', emailVerified: true }
const PROFILE = { id: 'c1', handle: 'davi', displayName: 'Davi', bio: null, createdAt: 'x', updatedAt: 'x' }
const UPLOAD_URL = 'https://upload.cloudflarestream.com/tus/abc'
const CREATED = {
  video: {
    id: 'v9',
    title: 'Minha aula',
    description: null,
    accessType: 'FREE',
    visibility: 'DRAFT',
    processingStatus: 'PENDING_UPLOAD',
    durationSeconds: null,
    thumbnailUrl: null,
    playerUrl: null,
    createdAt: '2026-02-01T10:00:00Z',
    updatedAt: '2026-02-01T10:00:00Z',
    publishedAt: null,
  },
  uploadUrl: UPLOAD_URL,
}
const session = {
  'GET /api/auth/me': () => jsonResponse(ME),
  'GET /api/creators/me': () => jsonResponse(PROFILE),
}

function renderPage() {
  return renderWithProviders(<UploadVideoPage />, {
    route: '/criador/videos/novo',
    path: '/criador/videos/novo',
    extraRoutes: [
      { path: '/entrar', element: <p>Página de login</p> },
      { path: '/criador/videos', element: <p>Lista de vídeos</p> },
      { path: '/criador/videos/:id', element: <p>Detalhe do vídeo</p> },
    ],
  })
}

function videoFile(size = 1000, type = 'video/mp4') {
  const file = new File(['x'], 'aula.mp4', { type })
  Object.defineProperty(file, 'size', { value: size })
  return file
}

async function fillAndSubmit(file: File | null = videoFile()) {
  const user = userEvent.setup({ applyAccept: false })
  await user.type(await screen.findByLabelText('Título'), 'Minha aula')
  if (file) await user.upload(screen.getByLabelText('Arquivo de vídeo'), file)
  await user.click(screen.getByRole('button', { name: 'Enviar vídeo' }))
  return user
}

beforeEach(() => {
  tus.instances.length = 0
  vi.mocked(Upload).mockClear()
})

describe('UploadVideoPage', () => {
  it('redirects to /entrar without a session', async () => {
    mockFetch({ 'GET /api/auth/me': () => errorResponse(401, 'UNAUTHENTICATED') })
    renderPage()
    expect(await screen.findByText('Página de login')).toBeInTheDocument()
  })

  it('shows a CTA when the user has no creator profile', async () => {
    mockFetch({
      'GET /api/auth/me': () => jsonResponse(ME),
      'GET /api/creators/me': () => errorResponse(404, 'CREATOR_PROFILE_NOT_FOUND'),
    })
    renderPage()
    expect(await screen.findByRole('link', { name: 'Ativar perfil de criador' })).toHaveAttribute(
      'href',
      '/criador/ativar',
    )
  })

  it('shows the 60-minute hint and accepts only video files', async () => {
    mockFetch(session)
    renderPage()
    const input = await screen.findByLabelText('Arquivo de vídeo')
    expect(input).toHaveAttribute('accept', 'video/*')
    expect(screen.getByText(/até 60 minutos/)).toBeInTheDocument()
  })

  it('validates title and file before calling the API', async () => {
    const spy = mockFetch(session)
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Enviar vídeo' }))
    expect(await screen.findByText('Informe o título.')).toBeInTheDocument()
    expect(screen.getByText('Selecione um arquivo de vídeo.')).toBeInTheDocument()
    expect(callsTo(spy, 'POST /api/creators/me/videos')).toHaveLength(0)
  })

  it('rejects a file larger than 30 GB', async () => {
    const spy = mockFetch(session)
    renderPage()
    await fillAndSubmit(videoFile(30 * 1024 ** 3 + 1))
    expect(await screen.findByText('Arquivo inválido ou maior que 30 GB.')).toBeInTheDocument()
    expect(callsTo(spy, 'POST /api/creators/me/videos')).toHaveLength(0)
  })

  it('creates the video with sizeBytes, uploads with tus, shows progress and navigates on success', async () => {
    const spy = mockFetch({
      ...session,
      'POST /api/creators/me/videos': () => jsonResponse(CREATED, 201),
    })
    renderPage()
    await fillAndSubmit(videoFile(2000))

    await waitFor(() => expect(tus.instances).toHaveLength(1))
    const calls = callsTo(spy, 'POST /api/creators/me/videos')
    expect(calls).toHaveLength(1)
    expect(sentJson(calls[0])).toEqual({
      title: 'Minha aula',
      description: null,
      accessType: 'FREE',
      sizeBytes: 2000,
    })

    const tusUpload = tus.instances[0]
    expect(tusUpload.options.uploadUrl).toBe(UPLOAD_URL)
    expect(tusUpload.options.chunkSize).toBe(50 * 1024 * 1024)
    expect(tusUpload.start).toHaveBeenCalled()
    // The browser never calls anything but our API (tus is mocked).
    expect(spy.mock.calls.every(([input]) => String(input).startsWith('/api'))).toBe(true)

    tusUpload.options.onProgress?.(500, 2000)
    expect(await screen.findByText('25%')).toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: 'Progresso do envio' })).toHaveAttribute('aria-valuenow', '25')

    tusUpload.options.onSuccess?.()
    expect(await screen.findByText('Detalhe do vídeo')).toBeInTheDocument()
  })

  it('cancels the upload', async () => {
    mockFetch({ ...session, 'POST /api/creators/me/videos': () => jsonResponse(CREATED, 201) })
    renderPage()
    const user = await fillAndSubmit()
    await waitFor(() => expect(tus.instances).toHaveLength(1))
    await user.click(await screen.findByRole('button', { name: 'Cancelar envio' }))
    expect(tus.instances[0].abort).toHaveBeenCalled()
    expect(await screen.findByText(/Envio cancelado/)).toBeInTheDocument()
    expect(screen.queryByText('Detalhe do vídeo')).not.toBeInTheDocument()
  })

  it('shows an error when the tus upload fails', async () => {
    mockFetch({ ...session, 'POST /api/creators/me/videos': () => jsonResponse(CREATED, 201) })
    renderPage()
    await fillAndSubmit()
    await waitFor(() => expect(tus.instances).toHaveLength(1))
    tus.instances[0].options.onError?.(new Error('network'))
    expect(await screen.findByText(/Falha no envio do vídeo/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Enviar vídeo' })).toBeEnabled()
  })

  it('shows VIDEO_PROVIDER_UNAVAILABLE and does not start the upload', async () => {
    mockFetch({
      ...session,
      'POST /api/creators/me/videos': () =>
        errorResponse(502, 'VIDEO_PROVIDER_UNAVAILABLE', 'Não foi possível falar com o serviço de vídeo. Tente novamente.'),
    })
    renderPage()
    await fillAndSubmit()
    expect(
      await screen.findByText('Não foi possível falar com o serviço de vídeo. Tente novamente.'),
    ).toBeInTheDocument()
    expect(tus.instances).toHaveLength(0)
  })

  it('shows EMAIL_NOT_VERIFIED', async () => {
    mockFetch({
      ...session,
      'POST /api/creators/me/videos': () =>
        errorResponse(403, 'EMAIL_NOT_VERIFIED', 'Confirme seu e-mail para continuar.'),
    })
    renderPage()
    await fillAndSubmit()
    expect(await screen.findByText('Confirme seu e-mail para continuar.')).toBeInTheDocument()
  })

  it('shows field errors returned by the server', async () => {
    mockFetch({
      ...session,
      'POST /api/creators/me/videos': () =>
        errorResponse(400, 'VALIDATION_ERROR', 'Dados inválidos.', {
          title: 'O título deve ter no máximo 100 caracteres.',
          sizeBytes: 'Arquivo inválido ou maior que 30 GB.',
        }),
    })
    renderPage()
    await fillAndSubmit()
    expect(await screen.findByText('O título deve ter no máximo 100 caracteres.')).toBeInTheDocument()
    expect(screen.getByText('Arquivo inválido ou maior que 30 GB.')).toBeInTheDocument()
    expect(tus.instances).toHaveLength(0)
  })
})
