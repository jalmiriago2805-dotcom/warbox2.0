import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ResetPasswordPage } from './ResetPasswordPage'
import { renderWithProviders } from '@/test/test-utils'
import { callsTo, emptyResponse, errorResponse, mockFetch, sentJson } from '@/test/fetch-mock'

function renderPage(route = '/redefinir-senha?token=tok123') {
  return renderWithProviders(<ResetPasswordPage />, { route, path: '/redefinir-senha' })
}

async function fill(newPassword: string, confirmation: string) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Nova senha'), newPassword)
  await user.type(screen.getByLabelText('Confirme a nova senha'), confirmation)
  await user.click(screen.getByRole('button', { name: 'Redefinir senha' }))
}

describe('ResetPasswordPage', () => {
  it('blocks submission when the passwords differ', async () => {
    const spy = mockFetch({})
    renderPage()
    await fill('senhanova123', 'senhanova124')
    expect(await screen.findByText('As senhas não coincidem.')).toBeInTheDocument()
    expect(callsTo(spy, 'POST /api/auth/password-reset/confirm')).toHaveLength(0)
  })

  it('posts {token,newPassword} and shows success with a link to login', async () => {
    const spy = mockFetch({ 'POST /api/auth/password-reset/confirm': () => emptyResponse() })
    renderPage()
    await fill('senhanova123', 'senhanova123')
    expect(await screen.findByText('Senha redefinida!')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Entrar' })).toHaveAttribute('href', '/entrar')
    const [init] = callsTo(spy, 'POST /api/auth/password-reset/confirm')
    expect(sentJson(init)).toEqual({ token: 'tok123', newPassword: 'senhanova123' })
  })

  it('shows the invalid link message on 400 INVALID_OR_EXPIRED_TOKEN', async () => {
    mockFetch({
      'POST /api/auth/password-reset/confirm': () => errorResponse(400, 'INVALID_OR_EXPIRED_TOKEN'),
    })
    renderPage()
    await fill('senhanova123', 'senhanova123')
    expect(await screen.findByText('Este link é inválido ou expirou.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Pedir um novo link' })).toHaveAttribute(
      'href',
      '/esqueci-senha',
    )
  })

  it('shows the error when there is no token', () => {
    mockFetch({})
    renderPage('/redefinir-senha')
    expect(screen.getByText('Este link é inválido ou expirou.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Pedir um novo link' })).toHaveAttribute(
      'href',
      '/esqueci-senha',
    )
    expect(screen.queryByLabelText('Nova senha')).not.toBeInTheDocument()
  })
})
