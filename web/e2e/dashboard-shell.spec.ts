import { expect, test } from '@playwright/test'

test.describe('Dashboard Shell (Mock API)', () => {
  test.beforeEach(async ({ page }) => {
    // Keep navigation regression isolated: no account creation or business writes.
    await page.route(/\/api\/(v1|web)\//, async (route) => {
      expect(route.request().method()).toBe('GET')
      const path = new URL(route.request().url()).pathname
      let data: unknown = []
      if (path === '/api/v1/auth/me') {
        data = { userId: 'dashboard-test-user', displayName: '测试用户', platformRoles: [], oauthProvider: 'local' }
      } else if (path.endsWith('/unread-count')) {
        data = { count: 0 }
      } else if (path === '/api/web/me/stars' || path === '/api/web/me/skills') {
        data = { items: [], total: 0, page: 0, size: 12 }
      }
      await route.fulfill({ json: { code: 0, msg: 'ok', data } })
    })
  })

  test('renders account navigation and overview links', async ({ page }) => {
    await page.goto('/dashboard')

    await expect(page.getByRole('heading', { name: '控制台', exact: true })).toBeVisible()
    const sidebar = page.getByRole('complementary')
    await expect(sidebar.getByRole('link', { name: '个人设置', exact: true })).toBeVisible()
    await expect(sidebar.getByRole('link', { name: '我的技能', exact: true })).toBeVisible()
    await expect(sidebar.getByRole('link', { name: '我的套件', exact: true })).toHaveAttribute('href', '/dashboard/suites')
    await expect(sidebar.getByRole('link', { name: 'Token 凭证', exact: true })).toBeVisible()
    await expect(page.getByText('查看和管理你发布的所有技能')).toBeVisible()
    await expect(page.getByText('查看和管理你的技能套件')).toBeVisible()
  })
})
