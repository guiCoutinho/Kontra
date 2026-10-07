import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { montarRotas } from '@/app/roteador'
import { renderizarAplicacaoEmRota } from '@/test/renderizar'

function PaginaQuebrada(): never {
  throw new Error('falha simulada de renderização')
}

describe('roteador', () => {
  beforeEach(() => {
    // A página inicial consulta /api/saude; aqui a resposta nunca chega (só importa a navegação).
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => new Promise(() => undefined))
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('mostra o título Kontra e o heading "Status do sistema" em /', () => {
    renderizarAplicacaoEmRota('/')

    expect(screen.getByRole('heading', { level: 1, name: 'Kontra' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: 'Status do sistema' })).toBeInTheDocument()
  })

  it('mostra "Página não encontrada" em rota inexistente e o link volta ao início', async () => {
    const usuario = userEvent.setup()
    renderizarAplicacaoEmRota('/rota-inexistente')

    expect(screen.getByRole('heading', { name: 'Página não encontrada' })).toBeInTheDocument()

    const link = screen.getByRole('link', { name: 'Voltar ao início' })
    expect(link).toHaveAttribute('href', '/')

    await usuario.click(link)

    expect(screen.getByRole('heading', { name: 'Status do sistema' })).toBeInTheDocument()
  })

  it('define o título do documento em /', async () => {
    renderizarAplicacaoEmRota('/')

    await waitFor(() => expect(document.title).toBe('Status do sistema | Kontra'))
  })

  it('define o título do documento em rota inexistente', async () => {
    renderizarAplicacaoEmRota('/rota-inexistente')

    await waitFor(() => expect(document.title).toBe('Página não encontrada | Kontra'))
  })

  it('mostra a página de erro em português, dentro do layout, quando a renderização falha', async () => {
    const consoleErro = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    renderizarAplicacaoEmRota(
      '/quebrada',
      montarRotas([{ path: '/quebrada', element: <PaginaQuebrada /> }]),
    )

    expect(screen.getByRole('heading', { level: 1, name: 'Kontra' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Algo deu errado' })).toBeInTheDocument()
    expect(screen.getByText(/Ocorreu um erro inesperado/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Voltar ao início' })).toHaveAttribute('href', '/')
    expect(screen.queryByText(/Unexpected Application Error/)).not.toBeInTheDocument()
    await waitFor(() => expect(document.title).toBe('Algo deu errado | Kontra'))

    consoleErro.mockRestore()
  })
})
