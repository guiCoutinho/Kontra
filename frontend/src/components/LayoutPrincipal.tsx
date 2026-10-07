import Box from '@mui/material/Box'
import Container from '@mui/material/Container'
import Typography from '@mui/material/Typography'
import { Outlet } from 'react-router'

export function LayoutPrincipal() {
  return (
    <Container maxWidth="md" sx={{ py: { xs: 2, sm: 4 } }}>
      <Box component="header" sx={{ mb: 3 }}>
        <Typography variant="h4" component="h1">
          Kontra
        </Typography>
      </Box>
      <Box component="main">
        <Outlet />
      </Box>
    </Container>
  )
}
