import type { ReactElement, ReactNode } from 'react'
import { QueryClient } from '@tanstack/react-query'
import { render, type RenderResult } from '@testing-library/react'
import { createMemoryRouter, MemoryRouter, type RouteObject } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { ProvedoresDaAplicacao } from '@/app/provedores'
import { rotas } from '@/app/roteador'

function criarClienteDeTeste(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
}

function renderizarNosProvedores(children: ReactNode): RenderResult {
  return render(
    <ProvedoresDaAplicacao clienteQuery={criarClienteDeTeste()}>{children}</ProvedoresDaAplicacao>,
  )
}

interface OpcoesDeRenderizacao {
  /** Rota inicial do MemoryRouter. Padrão: "/". */
  rotaInicial?: string
}

/** Renderiza um componente isolado com os mesmos provedores da aplicação (QueryClient sem retry). */
export function renderizarComProvedores(
  ui: ReactElement,
  { rotaInicial = '/' }: OpcoesDeRenderizacao = {},
): RenderResult {
  return renderizarNosProvedores(<MemoryRouter initialEntries={[rotaInicial]}>{ui}</MemoryRouter>)
}

/** Renderiza a aplicação no mesmo modo de produção (data router), em memória. */
export function renderizarAplicacaoEmRota(
  rotaInicial: string,
  rotasUsadas: RouteObject[] = rotas,
): RenderResult {
  const roteador = createMemoryRouter(rotasUsadas, { initialEntries: [rotaInicial] })
  return renderizarNosProvedores(<RouterProvider router={roteador} />)
}
