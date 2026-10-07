import type { ProblemaResponse } from '@/api/generated/model'

/**
 * Erro de uma resposta HTTP não-2xx.
 * `problema` vem preenchido quando o corpo é `application/problem+json`;
 * `corpo` guarda o corpo já decodificado (JSON ou texto) para qualquer outro caso
 * (ex.: o HTML de um 502 do nginx).
 */
export class ErroApi extends Error {
  readonly status: number
  readonly problema?: ProblemaResponse
  readonly corpo?: unknown

  constructor(status: number, problema?: ProblemaResponse, corpo?: unknown) {
    super(problema?.detail ?? `Falha na requisição (HTTP ${status})`)
    this.name = 'ErroApi'
    this.status = status
    this.problema = problema
    this.corpo = corpo
  }
}

// Lido pelo orval (override.mutator): tipo de erro dos hooks gerados. O parâmetro (erros declarados no contrato)
// é ignorado de propósito: status não-2xx vira ErroApi e falha de rede propaga como Error.
// O nome `ErrorType` é imposto pelo orval (exceção ao ADR-013).
export type ErrorType<_ErrosDoContrato> = ErroApi | Error

function tipoDoConteudo(resposta: Response): string {
  return (resposta.headers.get('Content-Type') ?? '').toLowerCase()
}

function ehObjeto(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === 'object' && valor !== null && !Array.isArray(valor)
}

/** JSON válido vira objeto; em resposta de erro, corpo "JSON" inválido cai para o texto cru. */
async function lerCorpo(resposta: Response): Promise<unknown> {
  const texto = await resposta.text()
  if (texto === '') return undefined

  if (!tipoDoConteudo(resposta).includes('json')) return texto
  try {
    return JSON.parse(texto)
  } catch (erro) {
    if (resposta.ok) throw erro
    return texto
  }
}

/**
 * Mutator do orval (ADR-003): faz a requisição e devolve o corpo da resposta 2xx.
 * Status não-2xx lança `ErroApi`; 2xx com JSON inválido lança `SyntaxError`;
 * erros de rede (ex.: `TypeError`) propagam como estão.
 */
export async function requisicaoApi<T>(url: string, opcoes: RequestInit): Promise<T> {
  const cabecalhos = new Headers(opcoes.headers)
  if (!cabecalhos.has('Accept')) {
    cabecalhos.set('Accept', 'application/json, application/problem+json')
  }
  const resposta = await fetch(url, { ...opcoes, headers: cabecalhos })
  const corpo = await lerCorpo(resposta)

  if (!resposta.ok) {
    const problema =
      tipoDoConteudo(resposta).includes('application/problem+json') && ehObjeto(corpo)
        ? (corpo as unknown as ProblemaResponse)
        : undefined
    throw new ErroApi(resposta.status, problema, corpo)
  }

  return corpo as T
}
