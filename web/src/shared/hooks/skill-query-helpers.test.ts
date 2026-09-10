import { describe, expect, it } from 'vitest'
import { buildSkillSearchUrl, shouldEnableNamespaceMemberCandidates } from './skill-query-helpers'

describe('buildSkillSearchUrl', () => {
  it('sends both business groups as distinct labels with ALL matching', () => {
    const url = new URL(buildSkillSearchUrl({ q: 'report', namespace: 'team', workflow: 'marketing',
      role: 'brand', resourceType: 'WEB', page: 2, size: 12 }), 'https://example.test')
    expect(url.searchParams.getAll('label')).toEqual(['marketing', 'brand'])
    expect(url.searchParams.get('labelMode')).toBe('ALL')
    expect(url.searchParams.get('resourceType')).toBe('WEB')
    expect(url.searchParams.get('page')).toBe('2')
  })

  it('deduplicates legacy and grouped labels', () => {
    const url = new URL(buildSkillSearchUrl({ label: 'brand', role: 'brand' }), 'https://example.test')
    expect(url.searchParams.getAll('label')).toEqual(['brand'])
    expect(url.searchParams.get('labelMode')).toBe('ALL')
  })
  it('combines a resource type with keyword, label and pagination', () => {
    const url = new URL(buildSkillSearchUrl({ q: '报告', label: 'official', resourceType: 'WEB', page: 2, size: 12 }), 'https://example.test')
    expect(Object.fromEntries(url.searchParams)).toEqual({ q: '报告', label: 'official', resourceType: 'WEB', page: '2', size: '12' })
  })

  it('normalizes the query and strips the namespace prefix', () => {
    expect(buildSkillSearchUrl({
      q: '  hello world  ',
      namespace: '@team-ai',
      label: 'code-generation',
      sort: 'relevance',
      page: 2,
      size: 12,
    })).toBe('/api/web/skills?q=hello+world&namespace=team-ai&label=code-generation&sort=relevance&page=2&size=12')
  })

  it('returns the base skills endpoint when no search params are provided', () => {
    expect(buildSkillSearchUrl({})).toBe('/api/web/skills')
  })

  it('keeps an empty q parameter when the search query is an empty string', () => {
    expect(buildSkillSearchUrl({ q: '' })).toBe('/api/web/skills?q=')
  })

  it('normalizes whitespace-only queries to an empty q parameter', () => {
    expect(buildSkillSearchUrl({
      q: '   ',
      sort: 'relevance',
      page: 0,
    })).toBe('/api/web/skills?q=&sort=relevance&page=0')
  })
})

describe('shouldEnableNamespaceMemberCandidates', () => {
  it('enables the query only when slug exists and search text has at least two non-space characters', () => {
    expect(shouldEnableNamespaceMemberCandidates('team-ai', 'ab')).toBe(true)
    expect(shouldEnableNamespaceMemberCandidates('team-ai', ' a ')).toBe(false)
    expect(shouldEnableNamespaceMemberCandidates('', 'admin')).toBe(false)
    expect(shouldEnableNamespaceMemberCandidates('team-ai', 'admin', false)).toBe(false)
  })
})
