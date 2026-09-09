import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mutateDemand } from './use-demands'
import { emptyDemand } from './demand-form'

const mocks = vi.hoisted(() => ({ fetchJson: vi.fn() }))
vi.mock('@/api/client', () => ({
  WEB_API_PREFIX: '/api/web', fetchJson: mocks.fetchJson,
  getCsrfHeaders: (initial?: HeadersInit) => {
    const headers = new Headers(initial)
    headers.set('X-XSRF-TOKEN', 'test-csrf')
    return headers
  },
}))

describe('demand requests', () => {
  beforeEach(() => mocks.fetchJson.mockReset())

  it('preserves the CSRF Headers object on publishing', async () => {
    await mutateDemand({ kind: 'create', content: { ...emptyDemand, title: 'Orders', scenario: 'Reconciliation', expectedResult: 'Exceptions' } })
    const [url, options] = mocks.fetchJson.mock.calls[0]
    expect(url).toBe('/api/web/demands')
    expect(options.headers.get('X-XSRF-TOKEN')).toBe('test-csrf')
    expect(options.headers.get('Content-Type')).toBe('application/json')
  })

  it('submits a scenario without automatically supporting the demand', async () => {
    await mutateDemand({ kind: 'supplement', id: 42, content: 'Refund orders' })
    expect(mocks.fetchJson).toHaveBeenCalledTimes(1)
    expect(mocks.fetchJson.mock.calls[0][0]).toBe('/api/web/demands/42/supplements')
  })

  it('withdraws support with the idempotent DELETE endpoint and CSRF header', async () => {
    await mutateDemand({ kind: 'support', id: 42, supported: false })
    const [url, options] = mocks.fetchJson.mock.calls[0]
    expect(url).toBe('/api/web/demands/42/support')
    expect(options.method).toBe('DELETE')
    expect(options.headers.get('X-XSRF-TOKEN')).toBe('test-csrf')
  })
})
