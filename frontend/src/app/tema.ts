import { createTheme } from '@mui/material/styles'
import { ptBR } from '@mui/material/locale'

export const tema = createTheme(
  {
    typography: {
      fontFamily:
        'system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
    },
    components: {
      MuiButtonBase: {
        styleOverrides: {
          root: {
            // Anel de foco por teclado visível em qualquer fundo (o ripple sozinho é fraco).
            '&.Mui-focusVisible': {
              outline: '2px solid currentColor',
              outlineOffset: '2px',
            },
          },
        },
      },
    },
  },
  ptBR,
)
