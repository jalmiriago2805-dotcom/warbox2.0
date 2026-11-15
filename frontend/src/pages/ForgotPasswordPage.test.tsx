import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ForgotPasswordPage } from './ForgotPasswordPage'
import { renderWithProviders } from '@/test/test-utils'
import { callsTo, emptyResponse, mockFetch, sentJson } from '@/test/fetch-mock'

const NEUTRAL =
  'Se existir uma conta com esse e-mail, enviaremos um link para redefinir sua senha.'

describe('ForgotPasswordPage', () => {
  it('posts the e-mail and shows the neutral message', async () => {
    const spy = mockFetch({ 'POST /api/auth/password-reset/request': () => emptyResponse() })
    renderWithProviders(<ForgotPasswordPage />, { route: '/esqueci-senha' })
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('E-mail'), 'ana@example.com')
    await user.click(screen.getByRole('button', { name: 'Enviar link' }))
    expect(await screen.findByText(NEUTRAL)).toBeInTheDocument()
    const [init] = callsTo(spy, 'POST /api/auth/password-reset/request')
    expect(sentJson(init)).toEqual({ email: 'ana@example.com' })
  })

  it('validates the e-mail format on the client', async () => {
    const spy = mockFetch({})
    renderWithProviders(<ForgotPasswordPage />, { route: '/esqueci-senha' })
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('E-mail'), 'ana')
    await user.click(screen.getByRole('button', { name: 'Enviar link' }))
    expect(await screen.findByText('Informe um e-mail válido.')).toBeInTheDocument()
    expect(callsTo(spy, 'POST /api/auth/password-reset/request')).toHaveLength(0)
  })
})
