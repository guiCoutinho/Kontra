import { QueryClient } from '@tanstack/react-query'

function statusDoErro(erro: unknown): number | undefined {
  if (typeof erro === 'object' && erro !== null && 'status' in erro) {
    const { status } = erro
    return typeof status === 'number' ? status : undefined
  }
  return undefined
}

/** Tenta de novo (uma vez) apenas erro de rede ou 5xx; nunca erro 4xx. */
export function deveTentarNovamente(tentativas: number, erro: unknown): boolean {
  if (tentativas >= 1) return false
  const status = statusDoErro(erro)
  return status === undefined || status >= 500
}

export function criarQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { staleTime: 30_000, retry: deveTentarNovamente },
      mutations: { retry: false },
    },
  })
}
