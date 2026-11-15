import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useHealth } from '@/features/health/useHealth'

export function HomePage() {
  const { isPending, isError, data } = useHealth()

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Warbox</h1>
        <p className="text-muted-foreground mt-2">
          Vídeos exclusivos dos seus criadores favoritos, por assinatura.
        </p>
      </div>
      <Card className="max-w-md">
        <CardHeader>
          <CardTitle>Status da API</CardTitle>
        </CardHeader>
        <CardContent aria-live="polite">
          {isPending ? (
            <p>Verificando…</p>
          ) : isError ? (
            <>
              <p className="font-medium">Indisponível</p>
              <p className="text-muted-foreground text-sm">Não foi possível conectar à API.</p>
            </>
          ) : (
            <p>
              <span className="font-medium">Online</span>{' '}
              <span data-testid="api-status" className="text-muted-foreground">
                {data.status}
              </span>
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
