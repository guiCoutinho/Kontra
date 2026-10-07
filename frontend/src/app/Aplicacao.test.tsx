import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Aplicacao } from '@/app/Aplicacao'

describe('Aplicacao', () => {
  it('mostra o título Kontra', () => {
    render(<Aplicacao />)

    expect(screen.getByRole('heading', { level: 1, name: 'Kontra' })).toBeInTheDocument()
  })

  it('limpa o DOM entre os testes (apenas um título por teste)', () => {
    render(<Aplicacao />)

    expect(screen.getAllByRole('heading')).toHaveLength(1)
  })
})
