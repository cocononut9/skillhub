// @vitest-environment jsdom

import { type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useMySuites, useResourceSearch, useSuiteDetail, useSuiteMemberCandidates, useSuiteVersions } from './use-suite-queries'

const mocks = vi.hoisted(() => ({
  auth: { user: { userId: 'user-a' } as { userId: string } | null, isLoading: false, error: null },
  fetchJson: vi.fn(),
}))
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => mocks.auth }))
vi.mock('@/api/client', () => ({ fetchJson: mocks.fetchJson, getCsrfHeaders: vi.fn(), suiteApi: {}, WEB_API_PREFIX: '/api/web' }))

let client: QueryClient
function Wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

beforeEach(() => {
  client = new QueryClient({ defaultOptions: { queries: { staleTime: 30_000, retry: false } } })
  mocks.auth = { user: { userId: 'user-a' }, isLoading: false, error: null }
  mocks.fetchJson.mockReset()
})
afterEach(() => { cleanup(); client.clear() })

const queries = [
  { name: 'detail', useSelectedQuery: () => useSuiteDetail('team', 'private-suite', '1.0.0') },
  { name: 'versions', useSelectedQuery: () => useSuiteVersions('team', 'private-suite') },
  { name: 'mine', useSelectedQuery: () => useMySuites() },
  { name: 'candidates', useSelectedQuery: () => useSuiteMemberCandidates('team', 'PRIVATE', '') },
  { name: 'discovery', useSelectedQuery: () => useResourceSearch({ resourceType: 'SUITE' }) },
]

describe.each(queries)('$name viewer isolation', ({ name, useSelectedQuery }) => {
  it('waits for identity resolution and permits only public queries for guests', async () => {
    mocks.auth = { user: null, isLoading: true, error: null }
    mocks.fetchJson.mockResolvedValue({ visibleTo: 'guest' })
    const { result, rerender } = renderHook(() => useSelectedQuery(), { wrapper: Wrapper })
    expect(result.current.isLoading).toBe(true)
    expect(mocks.fetchJson).not.toHaveBeenCalled()
    mocks.auth.isLoading = false
    rerender()
    if (name === 'mine' || name === 'candidates') {
      expect(mocks.fetchJson).not.toHaveBeenCalled()
      expect(result.current.data).toBeUndefined()
    } else {
      await waitFor(() => expect(result.current.data).toEqual({ visibleTo: 'guest' }))
    }
  })

  it('hides the previous account while the new account request is pending, including late responses', async () => {
    const privateData = { privateContent: 'user-a' }
    mocks.fetchJson.mockResolvedValueOnce(privateData)
    const { result, rerender } = renderHook(() => useSelectedQuery(), { wrapper: Wrapper })
    await waitFor(() => expect(result.current.data).toEqual(privateData))

    let completeOldRequest!: (value: unknown) => void
    mocks.fetchJson.mockImplementationOnce(() => new Promise(resolve => { completeOldRequest = resolve }))
    act(() => { void client.invalidateQueries() })
    await waitFor(() => expect(mocks.fetchJson).toHaveBeenCalledTimes(2))

    let completeNewRequest!: (value: unknown) => void
    mocks.fetchJson.mockImplementationOnce(() => new Promise(resolve => { completeNewRequest = resolve }))
    mocks.auth.user = { userId: 'user-b' }
    rerender()
    expect(result.current.data).toBeUndefined()
    await waitFor(() => expect(mocks.fetchJson).toHaveBeenCalledTimes(3))
    await act(async () => { completeOldRequest(privateData) })
    expect(result.current.data).toBeUndefined()
    await act(async () => { completeNewRequest({ visibleTo: 'user-b' }) })
    await waitFor(() => expect(result.current.data).toEqual({ visibleTo: 'user-b' }))
  })

  it('does not expose account data after becoming anonymous', async () => {
    mocks.fetchJson.mockResolvedValueOnce({ privateContent: 'user-a' })
    const { result, rerender } = renderHook(() => useSelectedQuery(), { wrapper: Wrapper })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    mocks.fetchJson.mockImplementation(() => new Promise(() => {}))
    mocks.auth.user = null
    rerender()
    expect(result.current.data).toBeUndefined()
  })
})

it('keeps pagination placeholders for the same viewer only', async () => {
  const pageData = { items: [{ slug: 'visible-suite' }], total: 2, page: 0, size: 1 }
  mocks.fetchJson.mockResolvedValueOnce(pageData)
  const { result, rerender } = renderHook(({ page }) => useResourceSearch({ resourceType: 'SUITE', page }), {
    initialProps: { page: 0 }, wrapper: Wrapper,
  })
  await waitFor(() => expect(result.current.data).toEqual(pageData))
  mocks.fetchJson.mockImplementation(() => new Promise(() => {}))
  rerender({ page: 1 })
  expect(result.current.data).toEqual(pageData)
  mocks.auth.user = { userId: 'user-b' }
  rerender({ page: 1 })
  expect(result.current.data).toBeUndefined()
})
