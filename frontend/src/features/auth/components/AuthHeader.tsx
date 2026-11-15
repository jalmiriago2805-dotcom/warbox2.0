import { Link, useNavigate } from 'react-router'
import { Button } from '@/components/ui/button'
import { useLogout, useMe } from '../hooks'

export function AuthHeader() {
  const { data: me, isPending } = useMe()
  const logout = useLogout()
  const navigate = useNavigate()

  if (isPending) return null

  if (!me) {
    return (
      <nav aria-label="Conta" className="flex items-center gap-2">
        <Button asChild variant="ghost" size="sm">
          <Link to="/entrar">Entrar</Link>
        </Button>
        <Button asChild size="sm">
          <Link to="/cadastro">Criar conta</Link>
        </Button>
      </nav>
    )
  }

  return (
    <nav aria-label="Conta" className="flex items-center gap-3">
      <span className="text-sm font-medium">{me.name}</span>
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={logout.isPending}
        onClick={() => logout.mutate(undefined, { onSuccess: () => navigate('/') })}
      >
        Sair
      </Button>
    </nav>
  )
}
