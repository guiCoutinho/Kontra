import { createBrowserRouter, type RouteObject } from 'react-router'
import { LayoutPrincipal } from '@/components/LayoutPrincipal'
import { PaginaDeErro } from '@/routes/PaginaDeErro'
import { PaginaInicial } from '@/routes/PaginaInicial'
import { PaginaNaoEncontrada } from '@/routes/PaginaNaoEncontrada'

/**
 * Monta a árvore de rotas. Erros de renderização das páginas caem na `PaginaDeErro`,
 * exibida dentro do layout. `extras` permite acrescentar rotas (usado nos testes).
 */
export function montarRotas(extras: RouteObject[] = []): RouteObject[] {
  return [
    {
      element: <LayoutPrincipal />,
      children: [
        {
          errorElement: <PaginaDeErro />,
          children: [
            { path: '/', element: <PaginaInicial /> },
            ...extras,
            { path: '*', element: <PaginaNaoEncontrada /> },
          ],
        },
      ],
    },
  ]
}

export const rotas = montarRotas()

export function criarRoteador() {
  return createBrowserRouter(rotas)
}
