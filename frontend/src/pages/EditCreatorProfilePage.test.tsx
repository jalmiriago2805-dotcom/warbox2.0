import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EditCreatorProfilePage } from './EditCreatorProfilePage'
import { renderWithProviders } from '@/test/test-utils'
import { callsTo, errorResponse, jsonResponse, mockFetch, sentJson } from '@/test/fetch-mock'

const ME = { id: 'u1', name: 'Ana', email: 'ana@example.com', emailVerified: true }
const PROFILE = { id: 'c1', handle: 'davi', displayName: 'Davi', bio: 'Oi', createdAt: 'x', updatedAt: 'x' }

function renderPage() {
  return renderWithProviders(<EditCreatorProfilePage />, {
    route: '/criador/editar',
    path: '/criador/editar',
    extraRoutes: [
      { path: '/entrar', element: <p>Página de login</p> },
      { path: '/criador/ativar', element: <p>Página de ativação</p> },
    ],
  })
}

describe('EditCreatorProfilePage', () => {
  it('redirects to /entrar without a session', async () => {
    mockFetch({ 'GET /api/auth/me': () => errorResponse(401, 'UNAUTHENTICATED') })
    renderPage()
    expect(await screen.findByText('Página de login')).toBeInTheDocument()
  })

  it('redirects to the activation page when there is no profile', async () => {
    mockFetch({
      'GET /api/auth/me': () => jsonResponse(ME),
      'GET /api/creators/me': () => errorResponse(404, 'CREATOR_PROFILE_NOT_FOUND'),
    })
    renderPage()
    expect(await screen.findByText('Página de ativação')).toBeInTheDocument()
  })

  it('pre-fills, saves with PUT and shows the success message', async () => {
    const spy = mockFetch({
      'GET /api/auth/me': () => jsonResponse(ME),
      'GET /api/creators/me': () => jsonResponse(PROFILE),
      'PUT /api/creators/me': () => jsonResponse({ ...PROFILE, displayName: 'Davi Lima' }),
    })
    renderPage()
    const user = userEvent.setup()
    expect(await screen.findByLabelText('Nome de usuário')).toHaveValue('davi')
    expect(screen.getByLabelText('Nome de exibição')).toHaveValue('Davi')
    expect(screen.getByLabelText('Bio')).toHaveValue('Oi')
    await user.clear(screen.getByLabelText('Nome de exibição'))
    await user.type(screen.getByLabelText('Nome de exibição'), 'Davi Lima')
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }))
    expect(await screen.findByText(/Perfil atualizado\./)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ver página pública' })).toHaveAttribute('href', '/c/davi')
    expect(sentJson(callsTo(spy, 'PUT /api/creators/me')[0])).toEqual({
      handle: 'davi',
      displayName: 'Davi Lima',
      bio: 'Oi',
    })
  })

  it('shows HANDLE_TAKEN on the handle field', async () => {
    mockFetch({
      'GET /api/auth/me': () => jsonResponse(ME),
      'GET /api/creators/me': () => jsonResponse(PROFILE),
      'PUT /api/creators/me': () =>
        errorResponse(409, 'HANDLE_TAKEN', 'Este nome de usuário já está em uso.', {
          handle: 'Este nome de usuário já está em uso.',
        }),
    })
    renderPage()
    const user = userEvent.setup()
    const handle = await screen.findByLabelText('Nome de usuário')
    await user.clear(handle)
    await user.type(handle, 'outro')
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }))
    expect(await screen.findByText('Este nome de usuário já está em uso.')).toBeInTheDocument()
    expect(screen.queryByText(/Perfil atualizado\./)).not.toBeInTheDocument()
  })
})
