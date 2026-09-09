/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PromptResourceContent } from './prompt-resource-content'

const { query, copy } = vi.hoisted(() => ({ query: vi.fn(), copy: vi.fn() }))
vi.mock('@/shared/hooks/use-skill-queries', () => ({ useSkillFile: query }))
vi.mock('@/shared/lib/clipboard', () => ({ copyToClipboard: copy }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
const props = { namespace: 'global', slug: 'demo', version: '1.0.0', enabled: true }
const prompt = '# 角色\r\n\r\n请保留 **格式** 与 `代码`。\n<script>example</script>\n'

describe('PromptResourceContent', () => {
  afterEach(cleanup)
  beforeEach(() => { query.mockReturnValue({ data: prompt, isLoading: false, error: null }); copy.mockReset(); copy.mockResolvedValue(undefined) })
  it('displays literal Markdown safely and copies the complete original text', async () => {
    const { container } = render(<PromptResourceContent {...props} />)
    expect(container.querySelector('pre')?.textContent).toBe(prompt)
    expect(container.querySelector('script')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'promptResource.copy' }))
    await waitFor(() => expect(copy).toHaveBeenCalledWith(prompt))
    expect(await screen.findByText('promptResource.copied')).toBeTruthy()
  })
  it('does not expose cached text or allow copying when unavailable', () => {
    render(<PromptResourceContent {...props} enabled={false} />)
    expect(document.querySelector('pre')).toBeNull()
    expect((screen.getByRole('button') as HTMLButtonElement).disabled).toBe(true)
    expect(query).toHaveBeenCalledWith('global', 'demo', '1.0.0', 'PROMPT.md', false)
  })
  it('does not offer stale text when loading fails', () => {
    query.mockReturnValue({ data: prompt, error: new Error('failed'), isLoading: false })
    render(<PromptResourceContent {...props} />)
    expect(document.querySelector('pre')).toBeNull()
    expect((screen.getByRole('button') as HTMLButtonElement).disabled).toBe(true)
  })
  it('reports clipboard errors instead of claiming success', async () => {
    copy.mockRejectedValue(new Error('denied'))
    render(<PromptResourceContent {...props} />)
    fireEvent.click(screen.getByRole('button'))
    expect(await screen.findByRole('alert')).toHaveProperty('textContent', 'promptResource.copyError')
  })
})
