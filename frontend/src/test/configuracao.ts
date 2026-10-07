import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterEach } from 'vitest'

afterEach(() => {
  cleanup()
})

// Padrão do Testing Library é 1 s; com a máquina (ou o CI) carregada, findBy/waitFor podem estourar sem haver defeito.
configure({ asyncUtilTimeout: 3000 })
