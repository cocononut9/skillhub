import { describe, expect, it } from 'vitest'
import { getWebsiteUrl } from './web-resource'
const metadata = (url: string) => JSON.stringify({ frontmatter: { resourceType: 'WEB', websiteUrl: url } })
describe('website entry', () => {
  it('reads the URL from version metadata', () => {
    expect(getWebsiteUrl(metadata('https://example.com/tool?q=1'))).toBe('https://example.com/tool?q=1')
  })
  it.each(['javascript:alert(1)', '//example.com', 'https://user:pass@example.com', 'data:text/html,hello', 'https:\\example.com'])('rejects unsafe destination %s', (url) => {
    expect(getWebsiteUrl(metadata(url))).toBeNull()
  })
  it('fails closed for missing or malformed metadata', () => {
    expect(getWebsiteUrl()).toBeNull()
    expect(getWebsiteUrl('{}')).toBeNull()
    expect(getWebsiteUrl('null')).toBeNull()
  })
})
