import { describe, expect, it, vi } from 'vitest'
import { criarQueryClient, deveTentarNovamente } from '@/lib/cliente-query'

async function contarChamadas(erro: unknown): Promise<number> {
  const cliente = criarQueryClient()
  const consulta = vi.fn().mockRejectedValue(erro)

  await expect(
    cliente.fetchQuery({ queryKey: ['teste'], queryFn: consulta, retryDelay: 0 }),
  ).rejects.toBe(erro)

  return consulta.mock.calls.length
}

describe('cliente-query', () => {
  it('tenta de novo uma vez em erro de rede', () => {
    expect(deveTentarNovamente(0, new TypeError('Failed to fetch'))).toBe(true)
    expect(deveTentarNovamente(1, new TypeError('Failed to fetch'))).toBe(false)
  })

  it('tenta de novo em erro 5xx, mas nunca em 4xx', () => {
    expect(deveTentarNovamente(0, { status: 503 })).toBe(true)
    expect(deveTentarNovamente(0, { status: 404 })).toBe(false)
  })

  it('usa os defaults da arquitetura', () => {
    const cliente = criarQueryClient()

    expect(cliente.getDefaultOptions().queries?.staleTime).toBe(30_000)
    expect(cliente.getDefaultOptions().mutations?.retry).toBe(false)
  })

  it('chama a consulta 2 vezes quando o erro é 503', async () => {
    expect(await contarChamadas({ status: 503 })).toBe(2)
  })

  it('chama a consulta 1 vez quando o erro é 404', async () => {
    expect(await contarChamadas({ status: 404 })).toBe(1)
  })

  it('chama a consulta 2 vezes quando o erro é de rede', async () => {
    expect(await contarChamadas(new TypeError('Failed to fetch'))).toBe(2)
  })
})
