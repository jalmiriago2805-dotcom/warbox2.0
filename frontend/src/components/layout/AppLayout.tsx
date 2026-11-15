import { Link, Outlet } from 'react-router'

export function AppLayout() {
  return (
    <div className="min-h-screen">
      <header className="border-b">
        <div className="mx-auto flex h-14 max-w-5xl items-center px-4">
          <Link to="/" className="text-primary text-lg font-bold tracking-tight">
            Warbox
          </Link>
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
