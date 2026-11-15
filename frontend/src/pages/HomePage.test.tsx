import { screen } from '@testing-library/react'
import { HomePage } from './HomePage'
import { renderWithProviders } from '@/test/test-utils'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('HomePage', () => {
  it('shows the brand and the loading state', () => {
    vi.spyOn(globalThis, 'fetch').mockReturnValue(new Promise(() => {}))
    renderWithProviders(<HomePage />)
    expect(screen.getByRole('heading', { level: 1, name: 'Warbox' })).toBeInTheDocument()
    expect(screen.getByText('Verificando…')).toBeInTheDocument()
  })

  it('shows Online when the API responds ok', async () => {
    const spy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ status: 'ok' }))
    renderWithProviders(<HomePage />)
    expect(await screen.findByText('Online')).toBeInTheDocument()
    expect(screen.getByTestId('api-status')).toHaveTextContent('ok')
    expect(spy).toHaveBeenCalledWith('/api/health', expect.anything())
  })

  it('shows Indisponível on a 503 response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({}, 503))
    renderWithProviders(<HomePage />)
    expect(await screen.findByText('Indisponível')).toBeInTheDocument()
    expect(screen.getByText('Não foi possível conectar à API.')).toBeInTheDocument()
  })

  it('shows Indisponível when the request rejects', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('network'))
    renderWithProviders(<HomePage />)
    expect(await screen.findByText('Indisponível')).toBeInTheDocument()
  })
})
