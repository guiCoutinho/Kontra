import { QueryClient } from '@tanstack/react-query'
import { ErroApi } from '@/lib/cliente-api'

/** Tenta de novo (uma vez) apenas erro de rede ou 5xx; nunca erro 4xx. */
export function deveTentarNovamente(tentativas: number, erro: unknown): boolean {
  if (tentativas >= 1) return false
  if (erro instanceof ErroApi) return erro.status >= 500
  return erro instanceof TypeError
}

export function criarQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { staleTime: 30_000, retry: deveTentarNovamente },
      mutations: { retry: false },
    },
  })
}
