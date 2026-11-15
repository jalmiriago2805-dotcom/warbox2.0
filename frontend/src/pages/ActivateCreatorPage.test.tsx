import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ActivateCreatorPage } from './ActivateCreatorPage'
import { renderWithProviders } from '@/test/test-utils'
import { callsTo, errorResponse, jsonResponse, mockFetch, sentJson } from '@/test/fetch-mock'

const ME = { id: 'u1', name: 'Ana', email: 'ana@example.com', emailVerified: true }
const PROFILE = { id: 'c1', handle: 'davi', displayName: 'Davi', bio: null, createdAt: 'x', updatedAt: 'x' }
const noProfile = { 'GET /api/creators/me': () => errorResponse(404, 'CREATOR_PROFILE_NOT_FOUND') }

function renderPage() {
  return renderWithProviders(<ActivateCreatorPage />, {
    route: '/criador/ativar',
    path: '/criador/ativar',
    extraRoutes: [
      { path: '/entrar', element: <p>Página de login</p> },
      { path: '/criador/editar', element: <p>Página de edição</p> },
      { path: '/c/:handle', element: <p>Página pública</p> },
    ],
  })
}

async function fill(handle: string, name = 'Davi Lima') {
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText('Nome de usuário'), handle)
  await user.type(screen.getByLabelText('Nome de exibição'), name)
  await user.click(screen.getByRole('button', { name: 'Ativar perfil' }))
}

describe('ActivateCreatorPage', () => {
  it('redirects to /entrar without a session', async () => {
    mockFetch({ 'GET /api/auth/me': () => errorResponse(401, 'UNAUTHENTICATED') })
    renderPage()
    expect(await screen.findByText('Página de login')).toBeInTheDocument()
  })

  it('redirects to the edit page when a profile exists', async () => {
    mockFetch({
      'GET /api/auth/me': () => jsonResponse(ME),
      'GET /api/creators/me': () => jsonResponse(PROFILE),
    })
    renderPage()
    expect(await screen.findByText('Página de edição')).toBeInTheDocument()
  })

  it('blocks an unverified user and sends nothing', async () => {
    const spy = mockFetch({
      'GET /api/auth/me': () => jsonResponse({ ...ME, emailVerified: false }),
      ...noProfile,
    })
    renderPage()
    expect(
      await screen.findByText('Confirme seu e-mail para ativar o perfil de criador.'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Nome de usuário')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Ativar perfil' })).toBeDisabled()
    expect(callsTo(spy, 'POST /api/creators/me')).toHaveLength(0)
  })

  it('posts the normalized handle and navigates to the public page', async () => {
    const spy = mockFetch({
      'GET /api/auth/me': () => jsonResponse(ME),
      ...noProfile,
      'POST /api/creators/me': () => jsonResponse(PROFILE, 201),
    })
    renderPage()
    await fill('  DaVi ')
    expect(await screen.findByText('Página pública')).toBeInTheDocument()
    expect(sentJson(callsTo(spy, 'POST /api/creators/me')[0])).toEqual({
      handle: 'davi',
      displayName: 'Davi Lima',
      bio: null,
    })
  })

  it('shows client validation errors without posting', async () => {
    const spy = mockFetch({ 'GET /api/auth/me': () => jsonResponse(ME), ...noProfile })
    renderPage()
    await fill('ab')
    expect(await screen.findByText(/^Use de 3 a 30 caracteres/)).toBeInTheDocument()
    expect(callsTo(spy, 'POST /api/creators/me')).toHaveLength(0)
  })

  it('shows HANDLE_TAKEN on the handle field', async () => {
    mockFetch({
      'GET /api/auth/me': () => jsonResponse(ME),
      ...noProfile,
      'POST /api/creators/me': () =>
        errorResponse(409, 'HANDLE_TAKEN', 'Este nome de usuário já está em uso.', {
          handle: 'Este nome de usuário já está em uso.',
        }),
    })
    renderPage()
    await fill('davi')
    expect(await screen.findByText('Este nome de usuário já está em uso.')).toBeInTheDocument()
    expect(screen.getByLabelText('Nome de usuário')).toHaveAttribute('aria-invalid', 'true')
  })

  it('shows server validation fields', async () => {
    mockFetch({
      'GET /api/auth/me': () => jsonResponse(ME),
      ...noProfile,
      'POST /api/creators/me': () =>
        errorResponse(400, 'VALIDATION_ERROR', 'Dados inválidos.', {
          displayName: 'O nome de exibição deve ter no máximo 60 caracteres.',
        }),
    })
    renderPage()
    await fill('davi')
    expect(
      await screen.findByText('O nome de exibição deve ter no máximo 60 caracteres.'),
    ).toBeInTheDocument()
  })
})
