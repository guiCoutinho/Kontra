import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router/dom'
import { ProvedoresDaAplicacao } from '@/app/provedores'
import { criarRoteador } from '@/app/roteador'

const raiz = document.getElementById('root')
if (!raiz) {
  throw new Error('Elemento #root não encontrado em index.html')
}

createRoot(raiz).render(
  <StrictMode>
    <ProvedoresDaAplicacao>
      <RouterProvider router={criarRoteador()} />
    </ProvedoresDaAplicacao>
  </StrictMode>,
)
