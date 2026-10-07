import { QueryClient } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { StatusComponente, type SaudeResponse } from '@/api/generated/model'
import { ProvedoresDaAplicacao } from '@/app/provedores'
import { StatusDoSistema } from '@/features/saude/StatusDoSistema'
import { renderizarComProvedores } from '@/test/renderizar'

// O vite.config.ts fixa TZ = America/Sao_Paulo (UTC-3) nos testes: 13:30:45Z → 10:30:45 local.
const verificadoEm = '2026-10-07T13:30:45.000Z'

function saudeCom(bancoDeDados: StatusComponente): SaudeResponse {
  return { status: bancoDeDados, bancoDeDados, verificadoEm } satisfies SaudeResponse
}

function resposta(corpo: SaudeResponse, status: number): Response {
  return new Response(JSON.stringify(corpo), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function PaginaDeTeste() {
  return (
    <>
      <h2 id="titulo-status">Status do sistema</h2>
      <StatusDoSistema idDoTitulo="titulo-status" />
    </>
  )
}

function renderizarStatus() {
  return renderizarComProvedores(<PaginaDeTeste />)
}

describe('StatusDoSistema', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('mostra o indicador de carregamento enquanto a consulta não termina', () => {
    vi.spyOn(globalThis, 'fetch').mockReturnValue(new Promise(() => undefined))
    renderizarStatus()

    expect(
      screen.getByRole('progressbar', { name: 'Verificando status do sistema' }),
    ).toBeInTheDocument()
  })

  it('mostra API e Banco de dados como Operacional quando tudo está disponível', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(resposta(saudeCom(StatusComponente.DISPONIVEL), 200)),
    )
    renderizarStatus()

    expect(await screen.findAllByText('Operacional')).toHaveLength(2)
    expect(screen.getByText('API', { selector: 'dt' })).toBeInTheDocument()
    expect(screen.getByText('Banco de dados', { selector: 'dt' })).toBeInTheDocument()
  })

  it('mostra o termo imediatamente seguido do seu valor (dl > dt + dd)', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(resposta(saudeCom(StatusComponente.DISPONIVEL), 200)),
    )
    renderizarStatus()

    const termo = await screen.findByText('API', { selector: 'dt' })

    expect(termo.parentElement?.tagName).toBe('DL')
    expect(termo.nextElementSibling?.tagName).toBe('DD')
    expect(termo.nextElementSibling).toHaveTextContent('Operacional')
  })

  it('mostra Indisponível para o banco quando a resposta 200 informa INDISPONIVEL', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(resposta(saudeCom(StatusComponente.INDISPONIVEL), 200)),
    )
    renderizarStatus()

    const termoBanco = await screen.findByText('Banco de dados', { selector: 'dt' })

    expect(termoBanco.nextElementSibling).toHaveTextContent('Indisponível')
    expect(screen.getByText('API', { selector: 'dt' }).nextElementSibling).toHaveTextContent(
      'Operacional',
    )
  })

  it('mostra a data da última verificação no formato dd/mm/aaaa hh:mm:ss', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(resposta(saudeCom(StatusComponente.DISPONIVEL), 200)),
    )
    renderizarStatus()

    expect(await screen.findByText('Última verificação: 07/10/2026 10:30:45')).toBeInTheDocument()
  })

  it('mostra o alerta de erro quando a resposta é 503', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(resposta(saudeCom(StatusComponente.INDISPONIVEL), 503)),
    )
    renderizarStatus()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não foi possível obter o status do sistema.',
    )
    expect(screen.getByRole('button', { name: 'Tentar novamente' })).toBeInTheDocument()
  })

  it('mostra o alerta de erro quando há falha de rede', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'))
    renderizarStatus()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não foi possível obter o status do sistema.',
    )
  })

  it('refaz a consulta ao clicar em "Tentar novamente" e mostra o resultado', async () => {
    const usuario = userEvent.setup()
    const fetchEspiao = vi
      .spyOn(globalThis, 'fetch')
      .mockImplementationOnce(() =>
        Promise.resolve(resposta(saudeCom(StatusComponente.INDISPONIVEL), 503)),
      )
      .mockImplementation(() =>
        Promise.resolve(resposta(saudeCom(StatusComponente.DISPONIVEL), 200)),
      )
    renderizarStatus()

    await usuario.click(await screen.findByRole('button', { name: 'Tentar novamente' }))

    expect(await screen.findAllByText('Operacional')).toHaveLength(2)
    expect(fetchEspiao).toHaveBeenCalledTimes(2)
  })

  it('foca a região "Status do sistema" com o resultado quando "Tentar novamente" dá certo', async () => {
    const usuario = userEvent.setup()
    vi.spyOn(globalThis, 'fetch')
      .mockImplementationOnce(() =>
        Promise.resolve(resposta(saudeCom(StatusComponente.INDISPONIVEL), 503)),
      )
      .mockImplementation(() =>
        Promise.resolve(resposta(saudeCom(StatusComponente.DISPONIVEL), 200)),
      )
    renderizarStatus()

    await usuario.click(await screen.findByRole('button', { name: 'Tentar novamente' }))
    await screen.findAllByText('Operacional')

    const regiao = screen.getByRole('region', { name: 'Status do sistema' })
    await waitFor(() => expect(document.activeElement).toBe(regiao))
    expect(regiao).toHaveTextContent('Operacional')
  })

  it('mantém o alerta montado e o foco no botão durante e depois de uma nova falha', async () => {
    const usuario = userEvent.setup()
    let falhar: () => void = () => undefined
    vi.spyOn(globalThis, 'fetch')
      .mockImplementationOnce(() =>
        Promise.resolve(resposta(saudeCom(StatusComponente.INDISPONIVEL), 503)),
      )
      .mockImplementation(
        () =>
          new Promise<Response>((resolver) => {
            falhar = () => resolver(resposta(saudeCom(StatusComponente.INDISPONIVEL), 503))
          }),
      )
    renderizarStatus()

    const botao = await screen.findByRole('button', { name: 'Tentar novamente' })
    await usuario.click(botao)

    // Durante a nova tentativa: mesmo botão, com foco, aria-disabled e texto "Tentando…".
    const tentando = await screen.findByRole('button', { name: 'Tentando…' })
    expect(tentando).toBe(botao)
    expect(tentando).toHaveAttribute('aria-disabled', 'true')
    expect(tentando).toHaveFocus()
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()

    falhar()

    const depois = await screen.findByRole('button', { name: 'Tentar novamente' })
    expect(depois).toBe(botao)
    expect(depois).toHaveFocus()
    expect(depois).toHaveAttribute('aria-disabled', 'false')
  })

  it('não usa regiões ao vivo aninhadas: o alerta não fica dentro de outra região ao vivo', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(resposta(saudeCom(StatusComponente.INDISPONIVEL), 503)),
    )
    renderizarStatus()

    const alerta = await screen.findByRole('alert')

    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(alerta.parentElement?.closest('[role="alert"], [role="status"], [aria-live]')).toBeNull()
  })

  it('não faz nova tentativa automática em erro 5xx', async () => {
    const fetchEspiao = vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(resposta(saudeCom(StatusComponente.INDISPONIVEL), 503)),
    )
    render(
      <ProvedoresDaAplicacao>
        <PaginaDeTeste />
      </ProvedoresDaAplicacao>,
    )

    await screen.findByRole('alert')

    expect(fetchEspiao).toHaveBeenCalledTimes(1)
  })

  it('mantém a lista, sem indicador de carregamento, durante uma atualização em segundo plano', async () => {
    const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    vi.spyOn(globalThis, 'fetch')
      .mockImplementationOnce(() =>
        Promise.resolve(resposta(saudeCom(StatusComponente.DISPONIVEL), 200)),
      )
      .mockImplementation(() => new Promise(() => undefined))
    render(
      <ProvedoresDaAplicacao clienteQuery={cliente}>
        <PaginaDeTeste />
      </ProvedoresDaAplicacao>,
    )
    await screen.findAllByText('Operacional')

    void cliente.invalidateQueries()

    await waitFor(() => expect(globalThis.fetch).toHaveBeenCalledTimes(2))
    expect(screen.getByText('API', { selector: 'dt' })).toBeInTheDocument()
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
    expect(document.activeElement).toBe(document.body)
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(document.querySelector('[aria-live]')).toBeNull()
  })

  it('mostra o alerta e some com "Operacional" quando um refetch em segundo plano falha com dados antigos', async () => {
    const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    vi.spyOn(globalThis, 'fetch')
      .mockImplementationOnce(() =>
        Promise.resolve(resposta(saudeCom(StatusComponente.DISPONIVEL), 200)),
      )
      .mockImplementation(() =>
        Promise.resolve(resposta(saudeCom(StatusComponente.INDISPONIVEL), 503)),
      )
    render(
      <ProvedoresDaAplicacao clienteQuery={cliente}>
        <PaginaDeTeste />
      </ProvedoresDaAplicacao>,
    )
    await screen.findAllByText('Operacional')

    void cliente.invalidateQueries()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não foi possível obter o status do sistema.',
    )
    expect(screen.queryByText('Operacional')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Tentar novamente' })).toBeInTheDocument()
  })

  it('mantém a lista quando um refetch em segundo plano termina com sucesso', async () => {
    const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(resposta(saudeCom(StatusComponente.DISPONIVEL), 200)),
    )
    render(
      <ProvedoresDaAplicacao clienteQuery={cliente}>
        <PaginaDeTeste />
      </ProvedoresDaAplicacao>,
    )
    await screen.findAllByText('Operacional')

    await cliente.invalidateQueries()

    expect(globalThis.fetch).toHaveBeenCalledTimes(2)
    expect(screen.getAllByText('Operacional')).toHaveLength(2)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('não puxa o foco de volta quando a pessoa já foi para outro elemento durante a nova tentativa', async () => {
    const usuario = userEvent.setup()
    let responder: () => void = () => undefined
    vi.spyOn(globalThis, 'fetch')
      .mockImplementationOnce(() =>
        Promise.resolve(resposta(saudeCom(StatusComponente.INDISPONIVEL), 503)),
      )
      .mockImplementation(
        () =>
          new Promise<Response>((resolver) => {
            responder = () => resolver(resposta(saudeCom(StatusComponente.DISPONIVEL), 200))
          }),
      )
    render(
      <>
        <button type="button">Outro controle</button>
        <ProvedoresDaAplicacao>
          <PaginaDeTeste />
        </ProvedoresDaAplicacao>
      </>,
    )

    await usuario.click(await screen.findByRole('button', { name: 'Tentar novamente' }))
    const outro = screen.getByRole('button', { name: 'Outro controle' })
    outro.focus()
    responder()
    await screen.findAllByText('Operacional')

    expect(outro).toHaveFocus()
  })
})
