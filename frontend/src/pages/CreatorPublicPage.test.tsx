import { screen } from '@testing-library/react'
import { CreatorPublicPage } from './CreatorPublicPage'
import { renderWithProviders } from '@/test/test-utils'
import { callsTo, errorResponse, jsonResponse, mockFetch } from '@/test/fetch-mock'

const PUBLIC = { handle: 'davi', displayName: 'Davi Lima', bio: 'Músico e produtor.', createdAt: '2026-01-01T00:00:00Z' }
const ME = { id: 'u1', name: 'Davi', email: 'd@example.com', emailVerified: true }
const MINE = { id: 'c1', ...PUBLIC, updatedAt: '2026-01-01T00:00:00Z' }
const loggedOut = { 'GET /api/auth/me': () => errorResponse(401, 'UNAUTHENTICATED') }

function renderPage(handle = 'davi') {
  return renderWithProviders(<CreatorPublicPage />, {
    route: `/c/${handle}`,
    path: '/c/:handle',
  })
}

describe('CreatorPublicPage', () => {
  it('renders the creator data and the placeholders', async () => {
    const spy = mockFetch({ ...loggedOut, 'GET /api/creators/davi': () => jsonResponse(PUBLIC) })
    renderPage()
    expect(await screen.findByRole('heading', { level: 1, name: 'Davi Lima' })).toBeInTheDocument()
    expect(screen.getByText('@davi')).toBeInTheDocument()
    expect(screen.getByText('Músico e produtor.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Vídeos' })).toBeInTheDocument()
    expect(screen.getByText('Os vídeos deste criador aparecerão aqui em breve.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Assinar' })).toBeDisabled()
    expect(screen.getByText('Em breve')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Editar perfil' })).not.toBeInTheDocument()
    expect(callsTo(spy, 'GET /api/creators/davi')).toHaveLength(1)
  })

  it('shows a fallback when there is no bio', async () => {
    mockFetch({ ...loggedOut, 'GET /api/creators/davi': () => jsonResponse({ ...PUBLIC, bio: null }) })
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
      'GET /api/auth/me': () => jsonResponse(ME),
      'GET /api/creators/davi': () => jsonResponse(PUBLIC),
      'GET /api/creators/me': () => jsonResponse(MINE),
    })
    renderPage()
    expect(await screen.findByRole('link', { name: 'Editar perfil' })).toHaveAttribute('href', '/criador/editar')
  })

  it('hides "Editar perfil" when the logged-in user owns another handle', async () => {
    const spy = mockFetch({
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
})
