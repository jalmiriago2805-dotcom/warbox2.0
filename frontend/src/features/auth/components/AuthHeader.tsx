import { Link, useNavigate } from 'react-router'
import { Button } from '@/components/ui/button'
import { useMyCreatorProfile } from '@/features/creator/hooks'
import { useLogout, useMe } from '../hooks'

export function AuthHeader() {
  const { data: me, isPending } = useMe()
  const logout = useLogout()
  const navigate = useNavigate()
  const { data: creator } = useMyCreatorProfile({ enabled: !!me })

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
      {creator ? (
        <>
          <Link to="/criador/videos" className="text-sm underline-offset-4 hover:underline">
            Meus vídeos
          </Link>
          <Link to={`/c/${creator.handle}`} className="text-sm underline-offset-4 hover:underline">
            Meu perfil
          </Link>
        </>
      ) : creator === null ? (
        <Link to="/criador/ativar" className="text-sm underline-offset-4 hover:underline">
          Seja criador
        </Link>
      ) : null}
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
