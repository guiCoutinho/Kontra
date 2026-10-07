import { useId } from 'react'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { StatusDoSistema } from '@/features/saude/StatusDoSistema'

export function PaginaInicial() {
  const idDoTitulo = useId()

  return (
    <Stack spacing={2}>
      <title>Status do sistema | Kontra</title>
      <Typography variant="h5" component="h2" id={idDoTitulo}>
        Status do sistema
      </Typography>
      <StatusDoSistema idDoTitulo={idDoTitulo} />
    </Stack>
  )
}
