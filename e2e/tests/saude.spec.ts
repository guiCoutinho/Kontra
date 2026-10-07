import { expect, test, type Page } from '@playwright/test'

type StatusSaude = 'DISPONIVEL' | 'INDISPONIVEL'

const corpoSaude = (status: StatusSaude) => ({
  status,
  bancoDeDados: status,
  verificadoEm: '2026-10-07T12:00:00.000Z',
})

/**
 * Intercepta GET /api/saude; `respostas[i]` atende a i-ésima chamada (a última se repete).
 * `segurarProxima()` faz as chamadas seguintes esperarem num "portão" até `liberar()`, para o teste
 * verificar o estado intermediário sem depender de tempo.
 */
async function interceptarSaude(page: Page, respostas: StatusSaude[]) {
  const contador = { chamadas: 0 }
  let portao: Promise<void> = Promise.resolve()
  let abrir: () => void = () => {}
  await page.route('**/api/saude', async (rota) => {
    const status = respostas[Math.min(contador.chamadas, respostas.length - 1)] ?? 'INDISPONIVEL'
    contador.chamadas += 1
    await portao
    await rota.fulfill({
      status: status === 'DISPONIVEL' ? 200 : 503,
      contentType: 'application/json',
      body: JSON.stringify(corpoSaude(status)),
    })
  })
  return {
    contador,
    segurarProxima: () => {
      portao = new Promise<void>((resolver) => {
        abrir = resolver
      })
    },
    liberar: () => abrir(),
  }
}

test.describe('Status do sistema', () => {
  test('deve exibir "Operacional" para a API e para o banco de dados na tela inicial', async ({ page }) => {
    await page.goto('/')

    await expect(page).toHaveTitle('Status do sistema | Kontra')
    await expect(page.getByRole('heading', { level: 1, name: 'Kontra' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Status do sistema' })).toBeVisible()

    // Contrato visual da T10: <dl> com os termos e valores, só por papéis.
    await expect(page.getByRole('term')).toHaveText(['API', 'Banco de dados'])
    await expect(page.getByRole('definition')).toHaveText(['Operacional', 'Operacional'])
    await expect(page.getByText(/^Última verificação: \d{2}\/\d{2}\/\d{4} \d{2}:\d{2}:\d{2}$/)).toBeVisible()
    await expect(page.getByRole('alert')).toHaveCount(0)
  })

  test('deve exibir o alerta de erro e recuperar a lista ao tentar novamente com sucesso', async ({ page }) => {
    const saude = await interceptarSaude(page, ['INDISPONIVEL', 'DISPONIVEL'])
    await page.goto('/')

    await expect(page.getByRole('alert')).toContainText('Não foi possível obter o status do sistema.')
    const botao = page.getByRole('button', { name: 'Tentar novamente' })
    await expect(botao).toBeVisible()
    expect(saude.contador.chamadas).toBe(1)

    saude.segurarProxima()
    await botao.click()

    // Com a resposta retida, o estado intermediário é estável: mesmo botão aria-disabled e alerta montado.
    await expect.poll(() => saude.contador.chamadas).toBe(2)
    await expect(page.getByRole('button', { name: 'Tentando…' })).toHaveAttribute('aria-disabled', 'true')
    await expect(page.getByRole('alert')).toBeVisible()

    saude.liberar()

    await expect(page.getByRole('term')).toHaveText(['API', 'Banco de dados'])
    await expect(page.getByRole('definition')).toHaveText(['Operacional', 'Operacional'])
    await expect(page.getByRole('alert')).toHaveCount(0)
    expect(saude.contador.chamadas).toBe(2)
    // No sucesso o foco vai para a região "Status do sistema".
    await expect(page.getByRole('region', { name: 'Status do sistema' })).toBeFocused()
  })

  test('deve manter o alerta e devolver o foco ao botão quando a nova tentativa também falhar', async ({ page }) => {
    const saude = await interceptarSaude(page, ['INDISPONIVEL'])
    await page.goto('/')
    // A 1ª resposta (503) precisa ter chegado antes de segurar a próxima.
    const botaoInicial = page.getByRole('button', { name: 'Tentar novamente' })
    await expect(botaoInicial).toBeVisible()

    saude.segurarProxima()
    await botaoInicial.click()

    await expect.poll(() => saude.contador.chamadas).toBe(2)
    const tentando = page.getByRole('button', { name: 'Tentando…' })
    await expect(tentando).toHaveAttribute('aria-disabled', 'true')
    // Tira o foco para provar que é a falha da nova tentativa que o devolve ao botão.
    await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur())
    await expect(tentando).not.toBeFocused()

    saude.liberar()

    const botao = page.getByRole('button', { name: 'Tentar novamente' })
    await expect(botao).toBeFocused()
    await expect(page.getByRole('alert')).toContainText('Não foi possível obter o status do sistema.')
    expect(saude.contador.chamadas).toBe(2)
  })
})

test.describe('API', () => {
  test('deve responder 200 com status DISPONIVEL em GET /api/saude pela origem do frontend', async ({ request }) => {
    const resposta = await request.get('/api/saude')

    expect(resposta.status()).toBe(200)
    expect(resposta.headers()['content-type']).toContain('application/json')
    expect(resposta.headers()['cache-control']).toBe('no-store')
    const corpo = await resposta.json()
    expect(corpo.status).toBe('DISPONIVEL')
    expect(corpo.bancoDeDados).toBe('DISPONIVEL')
    expect(typeof corpo.verificadoEm).toBe('string')
  })

  test('deve responder 401 em problem+json com NAO_AUTENTICADO em rota de API inexistente', async ({ request }) => {
    const resposta = await request.get('/api/inexistente')

    expect(resposta.status()).toBe(401)
    expect(resposta.headers()['content-type']).toContain('application/problem+json')
    expect(resposta.headers()['set-cookie']).toBeUndefined()
    const corpo = await resposta.json()
    expect(corpo.codigo).toBe('NAO_AUTENTICADO')
    expect(corpo.status).toBe(401)
    expect(corpo.title).toBe('Não autenticado')
  })
})
