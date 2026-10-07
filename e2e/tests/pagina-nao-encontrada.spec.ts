import { expect, test } from '@playwright/test'

test.describe('Página não encontrada', () => {
  test('deve exibir "Página não encontrada" e voltar ao início pelo link', async ({ page }) => {
    await page.goto('/rota-inexistente/da-spa')

    await expect(page.getByRole('heading', { name: 'Página não encontrada' })).toBeVisible()
    await expect(page).toHaveTitle('Página não encontrada | Kontra')

    await page.getByRole('link', { name: 'Voltar ao início' }).click()

    await expect(page).toHaveURL((url) => url.pathname === '/')
    await expect(page.getByRole('heading', { name: 'Status do sistema' })).toBeVisible()
  })
})
