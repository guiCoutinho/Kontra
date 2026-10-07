import { afterEach, describe, expect, it, vi } from 'vitest'
import { CodigoErro, StatusComponente } from '@/api/generated/model'
import type { ProblemaResponse, SaudeResponse } from '@/api/generated/model'
import { ErroApi, requisicaoApi } from '@/lib/cliente-api'

function responder(corpo: string, status: number, tipo: string): Response {
  return new Response(corpo, { status, headers: { 'Content-Type': tipo } })
}

function responderJson(corpo: unknown, status: number, tipo: string): Response {
  return responder(JSON.stringify(corpo), status, tipo)
}

async function capturarErro(promessa: Promise<unknown>): Promise<unknown> {
  try {
    await promessa
  } catch (erro) {
    return erro
  }
  throw new Error('A requisição deveria ter falhado')
}

const saude = {
  status: StatusComponente.DISPONIVEL,
  bancoDeDados: StatusComponente.DISPONIVEL,
  verificadoEm: '2026-10-07T10:00:00.000Z',
} satisfies SaudeResponse

describe('requisicaoApi', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('devolve o corpo quando a resposta é 200', async () => {
    const fetchEspiao = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(responderJson(saude, 200, 'application/json'))

    const resultado = await requisicaoApi<SaudeResponse>('/api/saude', { method: 'GET' })

    expect(resultado).toEqual(saude)
    expect(fetchEspiao).toHaveBeenCalledWith('/api/saude', expect.objectContaining({ method: 'GET' }))
  })

  it('devolve undefined quando a resposta 2xx tem corpo vazio', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 204 }))

    expect(await requisicaoApi('/api/qualquer', { method: 'DELETE' })).toBeUndefined()
  })

  it('lança SyntaxError quando a resposta 2xx declara JSON mas o corpo é inválido', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(responder('{quebrado', 200, 'application/json'))

    expect(await capturarErro(requisicaoApi('/api/saude', { method: 'GET' }))).toBeInstanceOf(
      SyntaxError,
    )
  })

  it('envia o Accept padrão quando o chamador não informa um', async () => {
    const fetchEspiao = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(responderJson(saude, 200, 'application/json'))

    await requisicaoApi('/api/saude', { method: 'GET' })

    const cabecalhos = new Headers(fetchEspiao.mock.calls[0]?.[1]?.headers)
    expect(cabecalhos.get('Accept')).toBe('application/json, application/problem+json')
  })

  it('preserva o Accept informado pelo chamador', async () => {
    const fetchEspiao = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(responderJson(saude, 200, 'application/json'))

    await requisicaoApi('/api/saude', { method: 'GET', headers: { Accept: 'text/plain' } })

    const cabecalhos = new Headers(fetchEspiao.mock.calls[0]?.[1]?.headers)
    expect(cabecalhos.get('Accept')).toBe('text/plain')
  })

  it('lança ErroApi com o problema quando a resposta é 400 application/problem+json', async () => {
    const problema = {
      type: 'about:blank',
      title: 'Dados inválidos',
      status: 400,
      detail: 'A requisição contém campos inválidos.',
      codigo: CodigoErro.ERRO_VALIDACAO,
      ocorridoEm: '2026-10-07T10:00:00.000Z',
    } satisfies ProblemaResponse
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      responderJson(problema, 400, 'Application/Problem+JSON'),
    )

    const erro = await capturarErro(requisicaoApi('/api/qualquer', { method: 'POST' }))

    expect(erro).toBeInstanceOf(ErroApi)
    expect(erro).toMatchObject({ status: 400, problema: { codigo: CodigoErro.ERRO_VALIDACAO } })
    expect((erro as ErroApi).message).toBe('A requisição contém campos inválidos.')
  })

  it('não trata um array application/problem+json como problema', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      responderJson([1, 2], 400, 'application/problem+json'),
    )

    const erro = await capturarErro(requisicaoApi('/api/qualquer', { method: 'GET' }))

    expect((erro as ErroApi).problema).toBeUndefined()
    expect((erro as ErroApi).corpo).toEqual([1, 2])
  })

  it('lança ErroApi sem problema, mas com corpo, quando a resposta é 503 application/json', async () => {
    const corpo = { status: 'INDISPONIVEL' }
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(responderJson(corpo, 503, 'application/json'))

    const erro = await capturarErro(requisicaoApi('/api/saude', { method: 'GET' }))

    expect(erro).toBeInstanceOf(ErroApi)
    expect((erro as ErroApi).status).toBe(503)
    expect((erro as ErroApi).problema).toBeUndefined()
    expect((erro as ErroApi).corpo).toEqual(corpo)
  })

  it('lança ErroApi com o corpo em texto quando a resposta é 502 text/html (nginx sem backend)', async () => {
    const html = '<html><body>502 Bad Gateway</body></html>'
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(responder(html, 502, 'text/html'))

    const erro = await capturarErro(requisicaoApi('/api/saude', { method: 'GET' }))

    expect(erro).toBeInstanceOf(ErroApi)
    expect((erro as ErroApi).status).toBe(502)
    expect((erro as ErroApi).problema).toBeUndefined()
    expect((erro as ErroApi).corpo).toBe(html)
    expect((erro as ErroApi).message).toBe('Falha na requisição (HTTP 502)')
  })

  it('propaga erro de rede como está', async () => {
    const falhaDeRede = new TypeError('Failed to fetch')
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(falhaDeRede)

    await expect(requisicaoApi('/api/saude', { method: 'GET' })).rejects.toBe(falhaDeRede)
  })
})
