import { expect, test } from '@playwright/test'

test.describe('Landing Navigation (Real API)', () => {
  test('searches from the combined homepage and preserves the query on reload', async ({ page }) => {
    await page.goto('/')

    await expect(page.getByRole('heading', { name: '发现好工具，分享好方法' })).toBeVisible()

    const searchInput = page.getByPlaceholder('搜索技能...')
    await searchInput.fill('agent ops')
    await searchInput.press('Enter')

    await expect.poll(() => new URL(page.url()).searchParams.get('q')).toBe('agent ops')
    expect(new URL(page.url()).pathname).toBe('/')
    expect(new URL(page.url()).searchParams.get('page')).toBe('0')
    await page.reload()
    await expect(searchInput).toHaveValue('agent ops')
  })

  test('redirects anonymous publish attempts to login', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('link', { name: '发布技能', exact: true }).click()
    await expect(page).toHaveURL(/\/login\?returnTo=%2Fdashboard%2Fpublish$/)
  })

  test('keeps the landing page within a 390px viewport', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/')

    await expect(page.getByRole('heading', { name: '发现好工具，分享好方法' })).toBeVisible()
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  })
})
