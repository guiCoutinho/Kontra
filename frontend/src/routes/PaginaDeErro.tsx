import Link from '@mui/material/Link'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { Link as LinkDoRoteador } from 'react-router'

export function PaginaDeErro() {
  return (
    <Stack spacing={2} sx={{ alignItems: 'flex-start' }}>
      <title>Algo deu errado | Kontra</title>
      <Typography variant="h5" component="h2">
        Algo deu errado
      </Typography>
      <Typography>
        Ocorreu um erro inesperado ao exibir esta página. Tente novamente em instantes.
      </Typography>
      <Link component={LinkDoRoteador} to="/">
        Voltar ao início
      </Link>
    </Stack>
  )
}
