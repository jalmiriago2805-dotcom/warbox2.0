import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { VerifyEmailPage } from './VerifyEmailPage'
import { renderWithProviders } from '@/test/test-utils'
import {
  callsTo,
  emptyResponse,
  errorResponse,
  jsonResponse,
  mockFetch,
  sentJson,
} from '@/test/fetch-mock'

const loggedOut = { 'GET /api/auth/me': () => errorResponse(401, 'UNAUTHENTICATED') }
const UNVERIFIED = { id: 'u1', name: 'Ana', email: 'ana@example.com', emailVerified: false }

function renderPage(route: string, strict = false) {
  return renderWithProviders(<VerifyEmailPage />, { route, path: '/verificar-email', strict })
}

describe('VerifyEmailPage', () => {
  it('posts the token exactly once, even in StrictMode', async () => {
    const spy = mockFetch({ ...loggedOut, 'POST /api/auth/verify-email': () => emptyResponse() })
    renderPage('/verificar-email?token=abc123', true)
    expect(await screen.findByText('E-mail confirmado!')).toBeInTheDocument()
    const calls = callsTo(spy, 'POST /api/auth/verify-email')
    expect(calls).toHaveLength(1)
    expect(sentJson(calls[0])).toEqual({ token: 'abc123' })
  })

  it('shows the verifying state, then success with a link home', async () => {
    let resolve!: (r: Response) => void
    mockFetch({
      ...loggedOut,
      'POST /api/auth/verify-email': () => new Promise<Response>((r) => (resolve = r)),
    })
    renderPage('/verificar-email?token=abc123')
    expect(await screen.findByText('Verificando seu e-mail…')).toBeInTheDocument()
    resolve(emptyResponse())
    expect(await screen.findByText('E-mail confirmado!')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ir para o início' })).toHaveAttribute('href', '/')
  })

  it('shows the invalid/expired message on 400 INVALID_OR_EXPIRED_TOKEN', async () => {
    mockFetch({
      ...loggedOut,
      'POST /api/auth/verify-email': () => errorResponse(400, 'INVALID_OR_EXPIRED_TOKEN'),
    })
    renderPage('/verificar-email?token=expired')
    expect(await screen.findByText('Este link é inválido ou expirou.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reenviar e-mail' })).not.toBeInTheDocument()
  })

  it('offers to resend when logged in and unverified', async () => {
    const spy = mockFetch({
      'GET /api/auth/me': () => jsonResponse(UNVERIFIED),
      'POST /api/auth/verify-email': () => errorResponse(400, 'INVALID_OR_EXPIRED_TOKEN'),
      'POST /api/auth/verify-email/resend': () => emptyResponse(),
    })
    renderPage('/verificar-email?token=expired')
    const button = await screen.findByRole('button', { name: 'Reenviar e-mail' })
    await userEvent.setup().click(button)
    expect(await screen.findByText('E-mail reenviado.')).toBeInTheDocument()
    expect(callsTo(spy, 'POST /api/auth/verify-email/resend')).toHaveLength(1)
  })

  it('shows the error without calling verify when there is no token', async () => {
    const spy = mockFetch(loggedOut)
    renderPage('/verificar-email')
    expect(await screen.findByText('Este link é inválido ou expirou.')).toBeInTheDocument()
    expect(callsTo(spy, 'POST /api/auth/verify-email')).toHaveLength(0)
  })
})
