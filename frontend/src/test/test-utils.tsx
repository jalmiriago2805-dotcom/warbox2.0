import { StrictMode, type ReactElement } from 'react'
import { render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router'

export type RenderWithProvidersOptions = {
  /** Initial URL (default `/`). */
  route?: string
  /** When set (or when `extraRoutes` is given), `ui` is mounted as the element of this route. */
  path?: string
  /** Sibling routes, e.g. `{ path: '/', element: <p>Início</p> }`, to assert navigation. */
  extraRoutes?: { path: string; element: ReactElement }[]
  /** Wraps the tree in <StrictMode>. */
  strict?: boolean
}

export function renderWithProviders(ui: ReactElement, opts: RenderWithProvidersOptions = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const withRoutes = opts.path !== undefined || opts.extraRoutes !== undefined
  const content = withRoutes ? (
    <Routes>
      <Route path={opts.path ?? '*'} element={ui} />
      {opts.extraRoutes?.map((r) => <Route key={r.path} path={r.path} element={r.element} />)}
    </Routes>
  ) : (
    ui
  )
  const tree = (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[opts.route ?? '/']}>{content}</MemoryRouter>
    </QueryClientProvider>
  )
  return { ...render(opts.strict ? <StrictMode>{tree}</StrictMode> : tree), queryClient }
}
