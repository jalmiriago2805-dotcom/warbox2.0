import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AppLayout } from './AppLayout'
import { renderWithProviders } from '@/test/test-utils'
import { callsTo, emptyResponse, errorResponse, jsonResponse, mockFetch } from '@/test/fetch-mock'

const VERIFIED = { id: 'u1', name: 'Ana Souza', email: 'ana@example.com', emailVerified: true }
const UNVERIFIED = { ...VERIFIED, emailVerified: false }
const BANNER_TEXT =
  'Confirme seu e-mail para aproveitar tudo no Warbox. Enviamos um link para ana@example.com.'

function renderLayout() {
  return renderWithProviders(<AppLayout />, { path: '/' })
}

describe('AppLayout', () => {
  it('shows Entrar / Criar conta and no banner when logged out', async () => {
    mockFetch({ 'GET /api/auth/me': () => errorResponse(401, 'UNAUTHENTICATED') })
    renderLayout()
    expect(await screen.findByRole('link', { name: 'Entrar' })).toHaveAttribute('href', '/entrar')
    expect(screen.getByRole('link', { name: 'Criar conta' })).toHaveAttribute('href', '/cadastro')
    expect(screen.queryByText(BANNER_TEXT)).not.toBeInTheDocument()
  })

  it('shows the name and Sair without a banner when verified', async () => {
    mockFetch({ 'GET /api/auth/me': () => jsonResponse(VERIFIED) })
    renderLayout()
    expect(await screen.findByText('Ana Souza')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sair' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Entrar' })).not.toBeInTheDocument()
    expect(screen.queryByText(BANNER_TEXT)).not.toBeInTheDocument()
  })

  it('renders nothing in the header while loading', () => {
    vi.spyOn(globalThis, 'fetch').mockReturnValue(new Promise(() => {}))
    renderLayout()
    expect(screen.getByRole('link', { name: 'Warbox' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Entrar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Sair' })).not.toBeInTheDocument()
  })

  it('shows the banner when unverified and resends the e-mail', async () => {
    const spy = mockFetch({
      'GET /api/auth/me': () => jsonResponse(UNVERIFIED),
      'POST /api/auth/verify-email/resend': () => emptyResponse(),
    })
    renderLayout()
    expect(await screen.findByText(BANNER_TEXT)).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Reenviar e-mail' }))
    expect(await screen.findByText('E-mail reenviado.')).toBeInTheDocument()
    expect(callsTo(spy, 'POST /api/auth/verify-email/resend')).toHaveLength(1)
  })

  it('shows the wait message on 429', async () => {
    mockFetch({
      'GET /api/auth/me': () => jsonResponse(UNVERIFIED),
      'POST /api/auth/verify-email/resend': () => errorResponse(429, 'TOO_MANY_REQUESTS'),
    })
    renderLayout()
    await userEvent.setup().click(await screen.findByRole('button', { name: 'Reenviar e-mail' }))
    expect(
      await screen.findByText('Aguarde um minuto antes de pedir outro e-mail.'),
    ).toBeInTheDocument()
  })

  it('shows "Seja criador" when the user has no creator profile', async () => {
    mockFetch({
      'GET /api/auth/me': () => jsonResponse(VERIFIED),
      'GET /api/creators/me': () => errorResponse(404, 'CREATOR_PROFILE_NOT_FOUND'),
    })
    renderLayout()
    expect(await screen.findByRole('link', { name: 'Seja criador' })).toHaveAttribute('href', '/criador/ativar')
    expect(screen.queryByRole('link', { name: 'Meu perfil' })).not.toBeInTheDocument()
  })

  it('shows "Meu perfil" linking to the public page when the user is a creator', async () => {
    mockFetch({
      'GET /api/auth/me': () => jsonResponse(VERIFIED),
      'GET /api/creators/me': () =>
        jsonResponse({ id: 'c1', handle: 'davi', displayName: 'Davi', bio: null, createdAt: 'x', updatedAt: 'x' }),
    })
    renderLayout()
    expect(await screen.findByRole('link', { name: 'Meu perfil' })).toHaveAttribute('href', '/c/davi')
    expect(screen.queryByRole('link', { name: 'Seja criador' })).not.toBeInTheDocument()
  })

  it('logs out and shows Entrar again', async () => {
    const spy = mockFetch({
      'GET /api/auth/me': () => jsonResponse(VERIFIED),
      'POST /api/auth/logout': () => emptyResponse(),
    })
    renderLayout()
    await userEvent.setup().click(await screen.findByRole('button', { name: 'Sair' }))
    expect(await screen.findByRole('link', { name: 'Entrar' })).toBeInTheDocument()
    expect(screen.queryByText('Ana Souza')).not.toBeInTheDocument()
    expect(callsTo(spy, 'POST /api/auth/logout')).toHaveLength(1)
  })
})
