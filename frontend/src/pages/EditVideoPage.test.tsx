import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EditVideoPage } from './EditVideoPage'
import { renderWithProviders } from '@/test/test-utils'
import { callsTo, emptyResponse, errorResponse, jsonResponse, mockFetch, sentJson } from '@/test/fetch-mock'

const ME = { id: 'u1', name: 'Ana', email: 'ana@example.com', emailVerified: true }
const PROFILE = { id: 'c1', handle: 'davi', displayName: 'Davi', bio: null, createdAt: 'x', updatedAt: 'x' }
const PLAYER = 'https://customer-abc.cloudflarestream.com/uid1/iframe'

function video(overrides: Record<string, unknown> = {}) {
  return {
    id: 'v1',
    title: 'Aula 1',
    description: 'Descrição',
    accessType: 'FREE',
    visibility: 'DRAFT',
    processingStatus: 'READY',
    durationSeconds: 125,
    thumbnailUrl: null,
    playerUrl: PLAYER,
    createdAt: '2026-02-01T10:00:00Z',
    updatedAt: '2026-02-01T10:00:00Z',
    publishedAt: null,
    ...overrides,
  }
}

const session = {
  'GET /api/auth/me': () => jsonResponse(ME),
  'GET /api/creators/me': () => jsonResponse(PROFILE),
}

function renderPage(id = 'v1') {
  return renderWithProviders(<EditVideoPage />, {
    route: `/criador/videos/${id}`,
    path: '/criador/videos/:id',
    extraRoutes: [
      { path: '/entrar', element: <p>Página de login</p> },
      { path: '/criador/videos', element: <p>Lista de vídeos</p> },
    ],
  })
}

describe('EditVideoPage', () => {
  it('redirects to /entrar without a session', async () => {
    mockFetch({ 'GET /api/auth/me': () => errorResponse(401, 'UNAUTHENTICATED') })
    renderPage()
    expect(await screen.findByText('Página de login')).toBeInTheDocument()
  })

  it('shows "Vídeo não encontrado" on 404', async () => {
    mockFetch({
      ...session,
      'GET /api/creators/me/videos/ghost': () => errorResponse(404, 'VIDEO_NOT_FOUND'),
    })
    renderPage('ghost')
    expect(await screen.findByRole('heading', { name: 'Vídeo não encontrado' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Voltar para Meus vídeos' })).toHaveAttribute('href', '/criador/videos')
  })

  it('shows the data, the status and the player preview', async () => {
    mockFetch({ ...session, 'GET /api/creators/me/videos/v1': () => jsonResponse(video()) })
    renderPage()
    expect(await screen.findByLabelText('Título')).toHaveValue('Aula 1')
    expect(screen.getByLabelText('Descrição')).toHaveValue('Descrição')
    expect(screen.getByLabelText('Gratuito', { exact: false })).toBeChecked()
    expect(screen.getByText('Pronto')).toBeInTheDocument()
    expect(screen.getByTitle('Prévia: Aula 1')).toHaveAttribute('src', PLAYER)
  })

  it('shows the processing notice and no preview while processing', async () => {
    mockFetch({
      ...session,
      'GET /api/creators/me/videos/v1': () =>
        jsonResponse(video({ processingStatus: 'PROCESSING', playerUrl: null, durationSeconds: null })),
    })
    renderPage()
    expect(await screen.findByText('Processando')).toBeInTheDocument()
    expect(screen.getByText(/ainda está sendo processado/)).toBeInTheDocument()
    expect(screen.queryByTitle('Prévia: Aula 1')).not.toBeInTheDocument()
  })

  it('saves the metadata with PUT', async () => {
    const user = userEvent.setup()
    const spy = mockFetch({
      ...session,
      'GET /api/creators/me/videos/v1': () => jsonResponse(video()),
      'PUT /api/creators/me/videos/v1': () => jsonResponse(video({ title: 'Novo título', accessType: 'EXCLUSIVE' })),
    })
    renderPage()
    const title = await screen.findByLabelText('Título')
    await user.clear(title)
    await user.type(title, '  Novo título ')
    await user.click(screen.getByLabelText('Exclusivo', { exact: false }))
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }))
    expect(await screen.findByText('Alterações salvas.')).toBeInTheDocument()
    const calls = callsTo(spy, 'PUT /api/creators/me/videos/v1')
    expect(calls).toHaveLength(1)
    expect(sentJson(calls[0])).toEqual({ title: 'Novo título', description: 'Descrição', accessType: 'EXCLUSIVE' })
  })

  it('validates the title before sending', async () => {
    const user = userEvent.setup()
    const spy = mockFetch({ ...session, 'GET /api/creators/me/videos/v1': () => jsonResponse(video()) })
    renderPage()
    await user.clear(await screen.findByLabelText('Título'))
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }))
    expect(await screen.findByText('Informe o título.')).toBeInTheDocument()
    expect(callsTo(spy, 'PUT /api/creators/me/videos/v1')).toHaveLength(0)
  })

  it('shows server field errors', async () => {
    const user = userEvent.setup()
    mockFetch({
      ...session,
      'GET /api/creators/me/videos/v1': () => jsonResponse(video()),
      'PUT /api/creators/me/videos/v1': () =>
        errorResponse(400, 'VALIDATION_ERROR', 'Dados inválidos.', { title: 'O título deve ter no máximo 100 caracteres.' }),
    })
    renderPage()
    await screen.findByLabelText('Título')
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }))
    expect(await screen.findByText('O título deve ter no máximo 100 caracteres.')).toBeInTheDocument()
  })

  it('shows the VIDEO_NOT_READY message when publishing fails', async () => {
    const user = userEvent.setup()
    mockFetch({
      ...session,
      'GET /api/creators/me/videos/v1': () => jsonResponse(video()),
      'POST /api/creators/me/videos/v1/publish': () =>
        errorResponse(409, 'VIDEO_NOT_READY', 'O vídeo ainda está sendo processado.'),
    })
    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Publicar' }))
    // Appears both in the status notice and in the error alert only when not READY; here it is the alert.
    expect(await screen.findByText('O vídeo ainda está sendo processado.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Publicar' })).toBeEnabled()
  })

  it('publishes and then offers to unpublish', async () => {
    const user = userEvent.setup()
    let published = false
    const current = () =>
      video(published ? { visibility: 'PUBLISHED', publishedAt: '2026-02-02T10:00:00Z' } : {})
    const spy = mockFetch({
      ...session,
      'GET /api/creators/me/videos/v1': () => jsonResponse(current()),
      'POST /api/creators/me/videos/v1/publish': () => {
        published = true
        return jsonResponse(current())
      },
      'POST /api/creators/me/videos/v1/unpublish': () => {
        published = false
        return jsonResponse(current())
      },
    })
    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Publicar' }))
    await user.click(await screen.findByRole('button', { name: 'Despublicar' }))
    expect(await screen.findByRole('button', { name: 'Publicar' })).toBeInTheDocument()
    expect(callsTo(spy, 'POST /api/creators/me/videos/v1/publish')).toHaveLength(1)
    expect(callsTo(spy, 'POST /api/creators/me/videos/v1/unpublish')).toHaveLength(1)
  })

  it('deletes after the confirmation and goes back to the list', async () => {
    const user = userEvent.setup()
    const spy = mockFetch({
      ...session,
      'GET /api/creators/me/videos/v1': () => jsonResponse(video()),
      'DELETE /api/creators/me/videos/v1': () => emptyResponse(204),
    })
    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Excluir Aula 1' }))
    expect(callsTo(spy, 'DELETE /api/creators/me/videos/v1')).toHaveLength(0)
    await user.click(screen.getByRole('button', { name: 'Confirmar exclusão' }))
    expect(await screen.findByText('Lista de vídeos')).toBeInTheDocument()
    expect(callsTo(spy, 'DELETE /api/creators/me/videos/v1')).toHaveLength(1)
  })
})
