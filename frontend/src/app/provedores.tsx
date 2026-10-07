import { useState, type ReactNode } from 'react'
import CssBaseline from '@mui/material/CssBaseline'
import { ThemeProvider } from '@mui/material/styles'
import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import { tema } from '@/app/tema'
import { criarQueryClient } from '@/lib/cliente-query'

interface ProvedoresDaAplicacaoProps {
  children: ReactNode
  /** Cliente de consultas; por padrão usa `criarQueryClient()` (os testes injetam um com `retry: false`). */
  clienteQuery?: QueryClient
}

export function ProvedoresDaAplicacao({ children, clienteQuery }: ProvedoresDaAplicacaoProps) {
  const [clientePadrao] = useState(criarQueryClient)

  return (
    <QueryClientProvider client={clienteQuery ?? clientePadrao}>
      <ThemeProvider theme={tema}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </QueryClientProvider>
  )
}
