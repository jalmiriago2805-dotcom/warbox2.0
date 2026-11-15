import { screen } from '@testing-library/react'
import { CreatorPublicPage } from './CreatorPublicPage'
import { renderWithProviders } from '@/test/test-utils'
import { callsTo, errorResponse, jsonResponse, mockFetch } from '@/test/fetch-mock'

const PUBLIC = { handle: 'davi', displayName: 'Davi Lima', bio: 'Músico e produtor.', createdAt: '2026-01-01T00:00:00Z' }
const ME = { id: 'u1', name: 'Davi', email: 'd@example.com', emailVerified: true }
const MINE = { id: 'c1', ...PUBLIC, updatedAt: '2026-01-01T00:00:00Z' }
const FREE_VIDEO = {
  id: 'v1',
  title: 'Aula aberta',
  description: null,
  accessType: 'FREE',
  durationSeconds: 125,
  publishedAt: '2026-02-01T10:00:00Z',
  locked: false,
  thumbnailUrl: 'https://customer-abc.cloudflarestream.com/uid1/thumbnails/thumbnail.jpg',
  playerUrl: 'https://customer-abc.cloudflarestream.com/uid1/iframe',
}
const EXCLUSIVE_VIDEO = {
  id: 'v2',
  title: 'Aula premium',
  description: null,
  accessType: 'EXCLUSIVE',
  durationSeconds: 3700,
  publishedAt: '2026-02-02T10:00:00Z',
  locked: true,
  thumbnailUrl: null,
  playerUrl: null,
}
const noVideos = { 'GET /api/creators/davi/videos': () => jsonResponse([]) }
const loggedOut = { 'GET /api/auth/me': () => errorResponse(401, 'UNAUTHENTICATED') }

function renderPage(handle = 'davi') {
  return renderWithProviders(<CreatorPublicPage />, {
    route: `/c/${handle}`,
    path: '/c/:handle',
  })
}

describe('CreatorPublicPage', () => {
  it('renders the creator data and the empty videos state', async () => {
    const spy = mockFetch({ ...loggedOut, ...noVideos, 'GET /api/creators/davi': () => jsonResponse(PUBLIC) })
    renderPage()
    expect(await screen.findByRole('heading', { level: 1, name: 'Davi Lima' })).toBeInTheDocument()
    expect(screen.getByText('@davi')).toBeInTheDocument()
    expect(screen.getByText('Músico e produtor.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Vídeos' })).toBeInTheDocument()
    expect(await screen.findByText('Este criador ainda não publicou vídeos.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Assinar' })).toBeDisabled()
    expect(screen.getByText('Em breve')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Editar perfil' })).not.toBeInTheDocument()
    expect(callsTo(spy, 'GET /api/creators/davi')).toHaveLength(1)
  })

  it('shows a fallback when there is no bio', async () => {
    mockFetch({ ...loggedOut, ...noVideos, 'GET /api/creators/davi': () => jsonResponse({ ...PUBLIC, bio: null }) })
    renderPage()
    expect(await screen.findByText('Este criador ainda não escreveu uma bio.')).toBeInTheDocument()
  })

  it('shows "Criador não encontrado" on 404', async () => {
    mockFetch({ ...loggedOut, 'GET /api/creators/ghost': () => errorResponse(404, 'CREATOR_NOT_FOUND') })
    renderPage('ghost')
    expect(await screen.findByText('Criador não encontrado')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Voltar para o início' })).toHaveAttribute('href', '/')
  })

  it('shows "Editar perfil" only for the owner', async () => {
    mockFetch({
      ...noVideos,
      'GET /api/auth/me': () => jsonResponse(ME),
      'GET /api/creators/davi': () => jsonResponse(PUBLIC),
      'GET /api/creators/me': () => jsonResponse(MINE),
    })
    renderPage()
    expect(await screen.findByRole('link', { name: 'Editar perfil' })).toHaveAttribute('href', '/criador/editar')
  })

  it('hides "Editar perfil" when the logged-in user owns another handle', async () => {
    const spy = mockFetch({
      ...noVideos,
      'GET /api/auth/me': () => jsonResponse(ME),
      'GET /api/creators/davi': () => jsonResponse(PUBLIC),
      'GET /api/creators/me': () => jsonResponse({ ...MINE, handle: 'outro' }),
    })
    renderPage()
    await screen.findByRole('heading', { level: 1, name: 'Davi Lima' })
    await vi.waitFor(() => expect(callsTo(spy, 'GET /api/creators/me')).toHaveLength(1))
    await new Promise((r) => setTimeout(r, 50))
    expect(screen.queryByRole('link', { name: 'Editar perfil' })).not.toBeInTheDocument()
  })

  it('renders free videos with the player iframe and exclusive ones locked without an iframe', async () => {
    mockFetch({
      ...loggedOut,
      'GET /api/creators/davi': () => jsonResponse(PUBLIC),
      'GET /api/creators/davi/videos': () => jsonResponse([FREE_VIDEO, EXCLUSIVE_VIDEO]),
    })
    const { container } = renderPage()
    const frame = await screen.findByTitle('Aula aberta')
    expect(frame.tagName).toBe('IFRAME')
    expect(frame).toHaveAttribute('src', FREE_VIDEO.playerUrl)
    expect(frame).toHaveAttribute('allow', 'accelerometer; gyroscope; autoplay; encrypted-media; picture-in-picture')
    expect(screen.getByText('2:05')).toBeInTheDocument()
    expect(screen.getByText('Aula premium')).toBeInTheDocument()
    expect(screen.getByText('Exclusivo para assinantes')).toBeInTheDocument()
    expect(screen.getByText('1:01:40')).toBeInTheDocument()
    expect(container.querySelectorAll('iframe')).toHaveLength(1)
  })

  it('shows an error when the videos cannot be loaded', async () => {
    mockFetch({
      ...loggedOut,
      'GET /api/creators/davi': () => jsonResponse(PUBLIC),
      'GET /api/creators/davi/videos': () => errorResponse(500, 'INTERNAL_ERROR'),
    })
    renderPage()
    expect(await screen.findByText('Não foi possível carregar os vídeos. Tente novamente mais tarde.')).toBeInTheDocument()
  })
})
