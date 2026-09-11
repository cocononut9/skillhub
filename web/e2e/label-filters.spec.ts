import { expect, test, type Page } from '@playwright/test'

// These fixtures are synthetic. Every API request is intercepted; no business data is written.
const catalog = [
  ["workflow-market-insight", "WORKFLOW", "市场洞察"],
  ["workflow-product-development", "WORKFLOW", "产品研发"],
  ["workflow-finished-goods-procurement", "WORKFLOW", "成品采购"],
  ["workflow-quality-inspection", "WORKFLOW", "品质质检"],
  ["workflow-cross-border-logistics", "WORKFLOW", "跨境物流"],
  ["workflow-overseas-warehousing", "WORKFLOW", "海外仓储"],
  ["workflow-brand-marketing", "WORKFLOW", "品牌营销"],
  ["workflow-platform-operations", "WORKFLOW", "平台运营"],
  ["workflow-multichannel-fulfillment", "WORKFLOW", "多渠道发货"],
  ["workflow-customer-service", "WORKFLOW", "客户服务"],
  ["influencer-screening", "ROLE", "品牌专员"],
  ["role-product-manager", "ROLE", "产品经理"],
  ["role-procurement-specialist", "ROLE", "采购专员"],
  ["role-quality-inspector", "ROLE", "质检专员"],
  ["role-logistics-specialist", "ROLE", "物流专员"],
  ["role-warehouse-specialist", "ROLE", "仓储专员"],
  ["role-platform-operator", "ROLE", "平台运营专员"],
  ["role-influencer-operator", "ROLE", "红人运营"],
  ["role-social-media-operator", "ROLE", "社媒运营"],
  ["role-customer-service-specialist", "ROLE", "客服专员"],
].map(([slug, category, displayName], sortOrder) => ({
  slug, category, displayName, sortOrder, type: 'RECOMMENDED', visibleInFilter: true,
  translations: [{ locale: 'zh', displayName }],
}))
const workflow = 'workflow-brand-marketing'
const role = 'influencer-screening'
const sample = (id: number, labels: string[], resourceType = 'WEB') => ({
  id, slug: 'test-report-' + id, displayName: '测试资源 ' + id, summary: '用于验证标签组合筛选的测试资源',
  namespace: 'global', resourceType, downloadCount: id, starCount: 1, ratingCount: 0,
  updatedAt: '2026-09-10T00:00:00Z', canSubmitPromotion: false,
  labels: catalog.filter((label) => labels.includes(label.slug)),
})
const resources = [sample(1, [workflow, role]), sample(2, [role]), sample(3, [workflow, role], 'PLUGIN')]

async function mockApi(page: Page) {
  const definitions = structuredClone(catalog)
  const searchRequests: URL[] = []
  const writes: Record<string, unknown>[] = []
  await page.addInitScript(() => localStorage.setItem('i18nextLng', 'zh'))
  await page.route(/\/api\/(v1|web)\//, async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    let data: unknown = []
    if (url.pathname === '/api/v1/auth/me') {
      data = { userId: 'test-user', displayName: '测试管理员', platformRoles: ['SUPER_ADMIN'] }
    } else if (url.pathname === '/api/web/labels') {
      data = definitions
    } else if (url.pathname === '/api/v1/admin/labels') {
      data = definitions
    } else if (url.pathname.startsWith('/api/v1/admin/labels/') && request.method() === 'PUT') {
      const body = request.postDataJSON()
      writes.push(body)
      const definition = definitions.find((item) => item.slug === decodeURIComponent(url.pathname.split('/').pop()!))!
      Object.assign(definition, body)
      data = definition
    } else if (url.pathname === '/api/web/skills') {
      searchRequests.push(url)
      const selected = url.searchParams.getAll('label')
      const matching = resources.filter((item) =>
        selected.every((slug) => item.labels.some((label) => label.slug === slug))
        && (!url.searchParams.get('resourceType') || item.resourceType === url.searchParams.get('resourceType')))
      data = { items: matching, total: matching.length, page: Number(url.searchParams.get('page') || 0), size: 12 }
    } else if (url.pathname === '/api/web/me/stars') {
      expect(url.searchParams.get('include')).toBe('labels')
      data = { items: resources, total: resources.length, page: 0, size: 100 }
    } else if (url.pathname.endsWith('/unread-count')) {
      data = { count: 0 }
    } else if (request.method() !== 'GET') {
      throw new Error('Unexpected write: ' + request.method() + ' ' + url.pathname)
    }
    await route.fulfill({ json: { code: 0, msg: 'ok', data } })
  })
  return { searchRequests, writes }
}

test('grouped filters, clearing, refresh and starred intersection work on desktop and mobile', async ({ page }, testInfo) => {
  const { searchRequests } = await mockApi(page)
  await page.goto('/search')
  const workflows = page.getByRole('group', { name: '流程标签', exact: true })
  const roles = page.getByRole('group', { name: '岗位标签', exact: true })
  await workflows.getByRole('button', { name: '品牌营销', exact: true }).click()
  await roles.getByRole('button', { name: '品牌专员', exact: true }).click()
  await page.getByRole('button', { name: '网页工具', exact: true }).click()
  await expect.poll(() => searchRequests.at(-1)?.searchParams.getAll('label')).toEqual([workflow, role])
  expect(searchRequests.at(-1)?.searchParams.get('labelMode')).toBe('ALL')
  await expect(page.getByText('测试资源 1', { exact: true })).toBeVisible()
  await expect(page.getByText('测试资源 2', { exact: true })).toHaveCount(0)
  await page.reload()
  await expect(workflows.getByRole('button', { name: '品牌营销', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await expect(roles.getByRole('button', { name: '品牌专员', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await page.getByRole('button', { name: '只看已收藏', exact: true }).click()
  await expect(page).toHaveURL(/starredOnly=true/)
  await expect(page.getByText('测试资源 1', { exact: true })).toBeVisible()
  await expect(page.getByText('测试资源 2', { exact: true })).toHaveCount(0)
  await expect(page.getByText('测试资源 3', { exact: true })).toHaveCount(0)
  await page.screenshot({ path: testInfo.outputPath('desktop.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(workflows).toBeVisible()
  await expect(roles).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('mobile.png'), fullPage: true })
  await workflows.getByRole('button', { name: '全部', exact: true }).click()
  await expect(roles.getByRole('button', { name: '品牌专员', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByText('测试资源 2', { exact: true })).toBeVisible()
})

test('combined homepage shows introduction and search with five navigation entries on desktop and mobile', async ({ page }) => {
  await mockApi(page)
  await page.goto('/')
  await expect(page).toHaveURL('http://127.0.0.1:3104/')
  await expect(page.locator('h1')).toBeVisible()
  await expect(page.getByRole('heading', { name: '发现好工具，分享好方法' })).toBeVisible()
  await expect(page.getByRole('group', { name: '流程标签', exact: true })).toBeVisible()
  const expectedNames = ['首页', '发布', '需求广场', '控制台', '我的技能']
  const expectedPaths = ['/', '/dashboard/publish', '/demands', '/dashboard', '/dashboard/skills']
  const desktopNav = page.locator('header nav')
  await expect(desktopNav.getByRole('link')).toHaveText(expectedNames)
  for (const [index, path] of expectedPaths.entries()) {
    await expect(desktopNav.getByRole('link').nth(index)).toHaveAttribute('href', path)
  }
  await expect(desktopNav.getByRole('link', { name: '首页', exact: true })).toHaveAttribute('aria-current', 'page')
  await expect(page.getByText('测试资源 1', { exact: true })).toBeVisible()

  await page.setViewportSize({ width: 1024, height: 900 })
  await expect(desktopNav).toBeVisible()
  await expect(page.getByRole('button', { name: '打开导航菜单' })).toBeHidden()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

  await page.setViewportSize({ width: 375, height: 812 })
  await expect(desktopNav).toBeHidden()
  await page.getByRole('button', { name: '打开导航菜单' }).click()
  const mobileNav = page.locator('nav').filter({ has: page.getByRole('link', { name: '需求广场', exact: true }) }).last()
  await expect(mobileNav.getByRole('link')).toHaveText(expectedNames)
  await mobileNav.getByRole('link', { name: '首页', exact: true }).click()
  await expect(page.getByRole('button', { name: '打开导航菜单' })).toBeVisible()
  await expect.poll(() => new URL(page.url()).pathname).toBe('/')
  await expect(page.locator('h1')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})

test('combined homepage keeps public search and login protection for anonymous visitors', async ({ page }) => {
  await mockApi(page)
  await page.route('**/api/v1/auth/me', (route) => route.fulfill({ status: 401, json: { code: 401, msg: 'Unauthorized' } }))
  await page.goto('/')
  await expect(page.locator('h1')).toBeVisible()
  await expect(page.locator('header nav').getByRole('link')).toHaveText(['首页'])
  await expect(page.getByRole('group', { name: '流程标签', exact: true })).toBeVisible()
  await page.goto('/dashboard/publish')
  await expect(page).toHaveURL(/\/login\?returnTo=/)
})

test('legacy search links preserve filters, query, sort and page on the homepage', async ({ page }) => {
  await mockApi(page)
  await page.goto('/search?q=测试&namespace=global&workflow=brand-marketing&role=brand-specialist&resourceType=WEB&sort=downloads&page=2&starredOnly=true')
  await expect.poll(() => new URL(page.url()).pathname).toBe('/')
  const params = new URL(page.url()).searchParams
  for (const [key, value] of Object.entries({
    q: '测试', namespace: 'global', workflow: 'brand-marketing', role: 'brand-specialist',
    resourceType: 'WEB', sort: 'downloads', page: '2', starredOnly: 'true',
  })) {
    expect(params.get(key)).toBe(value)
  }
  await expect(page.getByRole('heading', { name: '发现好工具，分享好方法' })).toBeVisible()
})

test('admin can edit business category without changing attachment permission type', async ({ page }) => {
  const { writes } = await mockApi(page)
  await page.goto('/admin/labels')
  const row = page.getByRole('row').filter({ hasText: '市场洞察' })
  await row.getByRole('button', { name: '编辑', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.locator('#label-category').click()
  await page.getByRole('option', { name: '岗位标签', exact: true }).click()
  await dialog.getByRole('button', { name: '保存修改', exact: true }).click()
  await expect.poll(() => writes.length).toBe(1)
  expect(writes[0]).toMatchObject({ category: 'ROLE', type: 'RECOMMENDED', visibleInFilter: true })
  await expect(dialog).toHaveCount(0)
  await expect(row).toContainText('岗位标签')
})
