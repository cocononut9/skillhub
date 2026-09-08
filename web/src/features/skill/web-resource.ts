/** Reads only HTTP(S) destinations from the selected version. */
export function getWebsiteUrl(metadataJson?: string | null): string | null {
  try {
    const metadata: unknown = JSON.parse(metadataJson ?? '')
    if (!metadata || typeof metadata !== 'object' || !('frontmatter' in metadata)) return null
    const fields = metadata.frontmatter
    if (!fields || typeof fields !== 'object' || !('resourceType' in fields) || fields.resourceType !== 'WEB'
      || !('websiteUrl' in fields) || typeof fields.websiteUrl !== 'string') return null
    const raw = fields.websiteUrl
    if (!/^https?:\/\//i.test(raw) || /[\s\\]/.test(raw) || raw.length > 2048) return null
    const url = new URL(raw)
    return ['http:', 'https:'].includes(url.protocol) && url.hostname && !url.username && !url.password ? url.href : null
  } catch {
    return null
  }
}
