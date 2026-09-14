// @vitest-environment jsdom
import { useState } from 'react'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PublishLabelPicker } from './publish-label-picker'

const catalog = vi.hoisted(() => ({ query: vi.fn(), refetch: vi.fn() }))
vi.mock('@/shared/hooks/use-label-queries', () => ({ useVisibleLabels: catalog.query }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))

function Picker() {
  const [selected, setSelected] = useState<string[]>([])
  return <><PublishLabelPicker selected={selected} onChange={setSelected} /><output>{selected.join(',')}</output></>
}

describe('PublishLabelPicker', () => {
  beforeEach(() => catalog.query.mockReturnValue({ data: [
    { slug: 'marketing', displayName: '红人营销', type: 'RECOMMENDED' },
    { slug: 'excel', displayName: '表格处理', type: 'RECOMMENDED' },
    { slug: 'official', displayName: '官方认证', type: 'PRIVILEGED' },
  ], isLoading: false, isError: false, refetch: catalog.refetch }))
  afterEach(cleanup)

  it('selects and removes existing labels, searches Chinese names, and excludes privileged labels', () => {
    const { container } = render(<Picker />)
    expect(screen.queryByText('官方认证')).toBeNull()
    fireEvent.click(screen.getByRole('checkbox', { name: '红人营销' }))
    expect(container.querySelector('output')?.textContent).toBe('marketing')
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '表格' } })
    expect(screen.queryByRole('checkbox', { name: '红人营销' })).toBeNull()
    fireEvent.click(screen.getByRole('checkbox', { name: '表格处理' }))
    expect(container.querySelector('output')?.textContent).toBe('marketing,excel')
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '' } })
    fireEvent.click(screen.getByRole('checkbox', { name: '红人营销' }))
    expect(container.querySelector('output')?.textContent).toBe('excel')
  })

  it('shows contact-admin guidance without a create action when search has no match', () => {
    render(<Picker />)
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '新标签' } })
    expect(screen.getByText('publish.labelsNoMatch')).toBeTruthy()
    expect(screen.queryByRole('button')).toBeNull()
    expect(screen.queryByRole('checkbox')).toBeNull()
  })

  it('allows retry after a catalog failure', () => {
    catalog.query.mockReturnValue({ isError: true, refetch: catalog.refetch })
    render(<Picker />)
    expect(screen.getByRole('alert').textContent).toContain('publish.labelsLoadError')
    fireEvent.click(screen.getByRole('button', { name: 'publish.labelsRetry' }))
    expect(catalog.refetch).toHaveBeenCalled()
  })
})
