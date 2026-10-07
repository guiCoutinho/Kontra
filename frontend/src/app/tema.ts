import { createTheme } from '@mui/material/styles'
import { ptBR } from '@mui/material/locale'

export const tema = createTheme(
  {
    typography: {
      fontFamily:
        'system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
    },
  },
  ptBR,
)
