import { expect, test } from '@playwright/test'

test.describe('Auth Entry (Real API)', () => {
  test('validates required fields and preserves returnTo on register link', async ({ page }) => {
    await page.goto('/login?returnTo=%2Fdashboard%2Ftokens')

    await expect(page.getByRole('heading', { name: '登录 SkillHub' })).toBeVisible()

    await page.getByRole('button', { name: '登录' }).click()
    await expect(page.getByText('请输入用户名')).toBeVisible()
    await expect(page.getByText('请输入密码')).toBeVisible()

    await page.getByRole('link', { name: '立即注册' }).click()
    await expect(page).toHaveURL('/register?returnTo=%2Fdashboard%2Ftokens')
  })
})
