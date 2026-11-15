import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { LoginPage } from './LoginPage'
import { renderWithProviders } from '@/test/test-utils'
import { callsTo, errorResponse, jsonResponse, mockFetch, sentJson } from '@/test/fetch-mock'

const ME = { id: 'u1', name: 'Ana', email: 'ana@example.com', emailVerified: true }
const loggedOut = { 'GET /api/auth/me': () => errorResponse(401, 'UNAUTHENTICATED') }

function renderPage() {
  return renderWithProviders(<LoginPage />, {
    route: '/entrar',
    path: '/entrar',
    extraRoutes: [{ path: '/', element: <p>Página inicial</p> }],
  })
}

async function submitCredentials() {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('E-mail'), 'ana@example.com')
  await user.type(screen.getByLabelText('Senha'), 'senhaforte123')
  await user.click(screen.getByRole('button', { name: 'Entrar' }))
}

describe('LoginPage', () => {
  it('posts the credentials and navigates home on success', async () => {
    const spy = mockFetch({ ...loggedOut, 'POST /api/auth/login': () => jsonResponse(ME) })
    const { queryClient } = renderPage()
    await submitCredentials()
    expect(await screen.findByText('Página inicial')).toBeInTheDocument()
    const [init] = callsTo(spy, 'POST /api/auth/login')
    expect(sentJson(init)).toEqual({ email: 'ana@example.com', password: 'senhaforte123' })
    expect(queryClient.getQueryData(['auth', 'me'])).toEqual(ME)
  })

  it('shows "E-mail ou senha incorretos." on 401 INVALID_CREDENTIALS', async () => {
    mockFetch({
      ...loggedOut,
      'POST /api/auth/login': () => errorResponse(401, 'INVALID_CREDENTIALS', 'Credenciais inválidas.'),
    })
    renderPage()
    await submitCredentials()
    expect(await screen.findByText('E-mail ou senha incorretos.')).toBeInTheDocument()
    expect(screen.queryByText('Página inicial')).not.toBeInTheDocument()
  })

  it('validates empty fields without calling the API', async () => {
    const spy = mockFetch(loggedOut)
    renderPage()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByText('Informe seu e-mail.')).toBeInTheDocument()
    expect(screen.getByText('Informe sua senha.')).toBeInTheDocument()
    expect(callsTo(spy, 'POST /api/auth/login')).toHaveLength(0)
  })

  it('links to password recovery and signup', () => {
    mockFetch(loggedOut)
    renderPage()
    expect(screen.getByRole('link', { name: 'Esqueci minha senha' })).toHaveAttribute(
      'href',
      '/esqueci-senha',
    )
    expect(screen.getByRole('link', { name: 'Criar conta' })).toHaveAttribute('href', '/cadastro')
  })

  it('redirects home when already logged in', async () => {
    mockFetch({ 'GET /api/auth/me': () => jsonResponse(ME) })
    renderPage()
    expect(await screen.findByText('Página inicial')).toBeInTheDocument()
  })
})
