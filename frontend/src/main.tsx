import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Aplicacao } from '@/app/Aplicacao'

const raiz = document.getElementById('root')
if (!raiz) {
  throw new Error('Elemento #root não encontrado em index.html')
}

createRoot(raiz).render(
  <StrictMode>
    <Aplicacao />
  </StrictMode>,
)
