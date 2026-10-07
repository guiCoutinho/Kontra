import Link from '@mui/material/Link'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { Link as LinkDoRoteador } from 'react-router'

export function PaginaNaoEncontrada() {
  return (
    <Stack spacing={2} sx={{ alignItems: 'flex-start' }}>
      <title>Página não encontrada | Kontra</title>
      <Typography variant="h5" component="h2">
        Página não encontrada
      </Typography>
      <Link component={LinkDoRoteador} to="/">
        Voltar ao início
      </Link>
    </Stack>
  )
}
