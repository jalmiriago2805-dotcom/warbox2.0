import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MyVideosPage } from './MyVideosPage'
import { renderWithProviders } from '@/test/test-utils'
import { callsTo, emptyResponse, errorResponse, jsonResponse, mockFetch } from '@/test/fetch-mock'

const ME = { id: 'u1', name: 'Ana', email: 'ana@example.com', emailVerified: true }
const PROFILE = { id: 'c1', handle: 'davi', displayName: 'Davi', bio: null, createdAt: 'x', updatedAt: 'x' }

function video(overrides: Record<string, unknown> = {}) {
  return {
    id: 'v1',
    title: 'Aula 1',
    description: null,
    accessType: 'FREE',
    visibility: 'DRAFT',
    processingStatus: 'READY',
    durationSeconds: 125,
    thumbnailUrl: null,
    playerUrl: null,
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

function renderPage() {
  return renderWithProviders(<MyVideosPage />, {
    route: '/criador/videos',
    path: '/criador/videos',
    extraRoutes: [{ path: '/entrar', element: <p>Página de login</p> }],
  })
}

describe('MyVideosPage', () => {
  it('redirects to /entrar without a session', async () => {
    mockFetch({ 'GET /api/auth/me': () => errorResponse(401, 'UNAUTHENTICATED') })
    renderPage()
    expect(await screen.findByText('Página de login')).toBeInTheDocument()
  })

  it('shows a CTA to activate the creator profile when there is none', async () => {
    mockFetch({
      'GET /api/auth/me': () => jsonResponse(ME),
      'GET /api/creators/me': () => errorResponse(404, 'CREATOR_PROFILE_NOT_FOUND'),
    })
    renderPage()
    expect(await screen.findByRole('link', { name: 'Ativar perfil de criador' })).toHaveAttribute(
      'href',
      '/criador/ativar',
    )
    expect(screen.queryByRole('link', { name: 'Enviar vídeo' })).not.toBeInTheDocument()
  })

  it('shows the empty state', async () => {
    mockFetch({ ...session, 'GET /api/creators/me/videos': () => jsonResponse([]) })
    renderPage()
    expect(await screen.findByText('Você ainda não enviou nenhum vídeo.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Enviar vídeo' })).toHaveAttribute('href', '/criador/videos/novo')
  })

  it('shows an error when the list cannot be loaded', async () => {
    mockFetch({ ...session, 'GET /api/creators/me/videos': () => errorResponse(500, 'INTERNAL_ERROR') })
    renderPage()
    expect(await screen.findByText('Não foi possível carregar seus vídeos. Tente novamente mais tarde.')).toBeInTheDocument()
  })

  it('renders the list with status, visibility and type, and disables publish until READY', async () => {
    mockFetch({
      ...session,
      'GET /api/creators/me/videos': () =>
        jsonResponse([
          video(),
          video({ id: 'v2', title: 'Aula 2', processingStatus: 'PROCESSING', accessType: 'EXCLUSIVE', durationSeconds: null }),
          video({ id: 'v3', title: 'Aula 3', visibility: 'PUBLISHED', publishedAt: '2026-02-02T10:00:00Z' }),
        ]),
    })
    renderPage()
    const items = await screen.findAllByRole('listitem')
    expect(items).toHaveLength(3)

    const first = within(items[0])
    expect(first.getByText('Aula 1')).toBeInTheDocument()
    expect(first.getByText('Pronto')).toBeInTheDocument()
    expect(first.getByText('Rascunho')).toBeInTheDocument()
    expect(first.getByText('Gratuito')).toBeInTheDocument()
    expect(first.getByRole('button', { name: 'Publicar Aula 1' })).toBeEnabled()
    expect(first.getByRole('link', { name: 'Editar Aula 1' })).toHaveAttribute('href', '/criador/videos/v1')

    const second = within(items[1])
    expect(second.getByText('Processando')).toBeInTheDocument()
    expect(second.getByText('Exclusivo')).toBeInTheDocument()
    expect(second.getByRole('button', { name: 'Publicar Aula 2' })).toBeDisabled()

    const third = within(items[2])
    expect(third.getByText('Publicado')).toBeInTheDocument()
    expect(third.getByRole('button', { name: 'Despublicar Aula 3' })).toBeEnabled()
  })

  it('publishes a READY video', async () => {
    const user = userEvent.setup()
    let published = false
    const spy = mockFetch({
      ...session,
      'GET /api/creators/me/videos': () => jsonResponse([video({ visibility: published ? 'PUBLISHED' : 'DRAFT' })]),
      'POST /api/creators/me/videos/v1/publish': () => {
        published = true
        return jsonResponse(video({ visibility: 'PUBLISHED', publishedAt: '2026-02-02T10:00:00Z' }))
      },
    })
    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Publicar Aula 1' }))
    expect(await screen.findByRole('button', { name: 'Despublicar Aula 1' })).toBeInTheDocument()
    expect(callsTo(spy, 'POST /api/creators/me/videos/v1/publish')).toHaveLength(1)
  })

  it('deletes only after the confirmation', async () => {
    const user = userEvent.setup()
    let deleted = false
    const spy = mockFetch({
      ...session,
      'GET /api/creators/me/videos': () => jsonResponse(deleted ? [] : [video()]),
      'DELETE /api/creators/me/videos/v1': () => {
        deleted = true
        return emptyResponse(204)
      },
    })
    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Excluir Aula 1' }))
    expect(callsTo(spy, 'DELETE /api/creators/me/videos/v1')).toHaveLength(0)

    // Cancelling keeps the video and sends nothing.
    await user.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(callsTo(spy, 'DELETE /api/creators/me/videos/v1')).toHaveLength(0)

    await user.click(screen.getByRole('button', { name: 'Excluir Aula 1' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar exclusão' }))
    expect(await screen.findByText('Você ainda não enviou nenhum vídeo.')).toBeInTheDocument()
    expect(callsTo(spy, 'DELETE /api/creators/me/videos/v1')).toHaveLength(1)
  })

  it('shows the API error when an action fails', async () => {
    const user = userEvent.setup()
    mockFetch({
      ...session,
      'GET /api/creators/me/videos': () => jsonResponse([video()]),
      'DELETE /api/creators/me/videos/v1': () =>
        errorResponse(502, 'VIDEO_PROVIDER_UNAVAILABLE', 'Não foi possível falar com o serviço de vídeo. Tente novamente.'),
    })
    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Excluir Aula 1' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar exclusão' }))
    expect(
      await screen.findByText('Não foi possível falar com o serviço de vídeo. Tente novamente.'),
    ).toBeInTheDocument()
    expect(screen.getByText('Aula 1')).toBeInTheDocument()
  })
})
