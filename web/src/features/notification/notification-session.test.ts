import { QueryClient } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { clearSessionScopedQueries, getNotificationQueryKeyScope } from './notification-session'

describe('getNotificationQueryKeyScope', () => {
  it('partitions notification caches by authenticated user id', () => {
    expect(getNotificationQueryKeyScope('user-a')).toEqual(['notifications', 'user-a'])
    expect(getNotificationQueryKeyScope('user-b')).toEqual(['notifications', 'user-b'])
  })

  it('falls back to a guest scope when there is no authenticated user', () => {
    expect(getNotificationQueryKeyScope(undefined)).toEqual(['notifications', 'guest'])
    expect(getNotificationQueryKeyScope(null)).toEqual(['notifications', 'guest'])
  })
})

describe('clearSessionScopedQueries', () => {
  it('removes Suite and resource caches before a new login can reuse them', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { staleTime: 30_000 } } })
    const keys = [
      ['suites', 'team', 'private-suite', '1.0.0', 'user-a'],
      ['suites', 'mine', '', 0, 20, 'user-a'],
      ['suites', 'team', 'private-suite', 'versions', 'user-a'],
      ['suites', 'member-candidates', 'team', 'PRIVATE', '', 'user-a'],
      ['resources', 'search', { resourceType: 'SUITE' }, 'user-a'],
    ]
    for (const key of keys) queryClient.setQueryData(key, { privateContent: 'user-a' })

    clearSessionScopedQueries(queryClient)

    for (const key of keys) expect(queryClient.getQueryData(key)).toBeUndefined()
    const result = await queryClient.fetchQuery({ queryKey: keys[0], queryFn: async () => null })
    expect(result).toBeNull()
    queryClient.clear()
  })

  it('removes user-scoped notification and dashboard caches without touching public search caches', () => {
    const queryClient = new QueryClient()
    queryClient.setQueryData(['demands', 42], { hidden: true, mine: true })
    queryClient.setQueryData(['notifications', 'user-a', 'unread-count'], { count: 3 })
    queryClient.setQueryData(['labels', 'visible'], [{ slug: 'official' }])
    queryClient.setQueryData(['skills', 'my', { page: 0, size: 12 }], { items: [] })
    queryClient.setQueryData(['skills', 'search', { q: '', sort: 'relevance', page: 0, size: 12, starredOnly: false }], { items: [] })

    clearSessionScopedQueries(queryClient)

    expect(queryClient.getQueryData(['demands', 42])).toBeUndefined()
    expect(queryClient.getQueryData(['notifications', 'user-a', 'unread-count'])).toBeUndefined()
    expect(queryClient.getQueryData(['labels', 'visible'])).toBeUndefined()
    expect(queryClient.getQueryData(['skills', 'my', { page: 0, size: 12 }])).toBeUndefined()
    expect(queryClient.getQueryData(['skills', 'search', { q: '', sort: 'relevance', page: 0, size: 12, starredOnly: false }])).toEqual({ items: [] })
  })
})
