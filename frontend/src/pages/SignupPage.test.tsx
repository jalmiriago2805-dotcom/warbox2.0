import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SignupPage } from './SignupPage'
import { renderWithProviders } from '@/test/test-utils'
import { callsTo, errorResponse, jsonResponse, mockFetch, sentJson } from '@/test/fetch-mock'

const ME = { id: 'u1', name: 'Ana', email: 'ana@example.com', emailVerified: false }
const loggedOut = { 'GET /api/auth/me': () => errorResponse(401, 'UNAUTHENTICATED') }

function renderPage() {
  return renderWithProviders(<SignupPage />, {
    route: '/cadastro',
    path: '/cadastro',
    extraRoutes: [{ path: '/', element: <p>Página inicial</p> }],
  })
}

function yearsAgo(years: number): string {
  return `${new Date().getFullYear() - years}-01-01`
}

async function fillForm(opts: { birthDate?: string; acceptTerms?: boolean } = {}) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Nome'), 'Ana Souza')
  await user.type(screen.getByLabelText('E-mail'), 'ana@example.com')
  await user.type(screen.getByLabelText('Senha'), 'senhaforte123')
  fireEvent.change(screen.getByLabelText('Data de nascimento'), {
    target: { value: opts.birthDate ?? '1990-05-20' },
  })
  if (opts.acceptTerms ?? true) {
    await user.click(
      screen.getByRole('checkbox', {
        name: 'Tenho 18 anos ou mais e aceito os Termos de Uso e a Política de Privacidade',
      }),
    )
  }
  return user
}

const submit = (user: ReturnType<typeof userEvent.setup>) =>
  user.click(screen.getByRole('button', { name: 'Criar conta' }))

describe('SignupPage', () => {
  it('blocks a minor on the client without calling signup', async () => {
    const spy = mockFetch(loggedOut)
    renderPage()
    const user = await fillForm({ birthDate: yearsAgo(10) })
    await submit(user)
    expect(
      await screen.findByText('Você precisa ter 18 anos ou mais para criar uma conta.'),
    ).toBeInTheDocument()
    expect(callsTo(spy, 'POST /api/auth/signup')).toHaveLength(0)
  })

  it('blocks submission when the terms are not accepted', async () => {
    const spy = mockFetch(loggedOut)
    renderPage()
    const user = await fillForm({ acceptTerms: false })
    await submit(user)
    expect(
      await screen.findByText('Você precisa aceitar os Termos de Uso e a Política de Privacidade.'),
    ).toBeInTheDocument()
    expect(callsTo(spy, 'POST /api/auth/signup')).toHaveLength(0)
  })

  it('posts the exact payload and navigates home on success', async () => {
    const spy = mockFetch({
      ...loggedOut,
      'POST /api/auth/signup': () => jsonResponse(ME, 201),
    })
    renderPage()
    const user = await fillForm()
    await submit(user)
    expect(await screen.findByText('Página inicial')).toBeInTheDocument()
    const [init] = callsTo(spy, 'POST /api/auth/signup')
    expect(sentJson(init)).toEqual({
      name: 'Ana Souza',
      email: 'ana@example.com',
      password: 'senhaforte123',
      birthDate: '1990-05-20',
      acceptedTerms: true,
    })
    expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBeTruthy()
  })

  it('shows 409 EMAIL_TAKEN on the e-mail field', async () => {
    mockFetch({
      ...loggedOut,
      'POST /api/auth/signup': () => errorResponse(409, 'EMAIL_TAKEN', 'E-mail já cadastrado.'),
    })
    renderPage()
    const user = await fillForm()
    await submit(user)
    await waitFor(() =>
      expect(screen.getByLabelText('E-mail')).toHaveAccessibleDescription(
        'Este e-mail já está cadastrado.',
      ),
    )
    expect(screen.getByLabelText('E-mail')).toHaveAttribute('aria-invalid', 'true')
  })

  it('shows the server 400 UNDERAGE message', async () => {
    mockFetch({
      ...loggedOut,
      'POST /api/auth/signup': () => errorResponse(400, 'UNDERAGE', 'Menor de idade.'),
    })
    renderPage()
    const user = await fillForm()
    await submit(user)
    expect(
      await screen.findByText('Você precisa ter 18 anos ou mais para criar uma conta.'),
    ).toBeInTheDocument()
  })

  it('maps VALIDATION_ERROR fields to the inputs', async () => {
    mockFetch({
      ...loggedOut,
      'POST /api/auth/signup': () =>
        errorResponse(400, 'VALIDATION_ERROR', 'Dados inválidos.', { name: 'Nome muito longo.' }),
    })
    renderPage()
    const user = await fillForm()
    await submit(user)
    await waitFor(() =>
      expect(screen.getByLabelText('Nome')).toHaveAccessibleDescription('Nome muito longo.'),
    )
  })

  it('redirects home when already logged in', async () => {
    mockFetch({ 'GET /api/auth/me': () => jsonResponse(ME) })
    renderPage()
    expect(await screen.findByText('Página inicial')).toBeInTheDocument()
  })
})
