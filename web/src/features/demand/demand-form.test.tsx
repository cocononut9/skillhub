/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { DemandForm } from './demand-form'

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
afterEach(cleanup)

describe('DemandForm', () => {
  it('rejects whitespace-only required input, then submits trimmed business content', () => {
    const save = vi.fn()
    render(<DemandForm pending={false} onSave={save} onCancel={vi.fn()} />)
    fireEvent.change(screen.getByLabelText('demand.title *'), { target: { value: '   ' } })
    fireEvent.change(screen.getByLabelText('demand.scenario *'), { target: { value: '  Reconcile orders  ' } })
    fireEvent.change(screen.getByLabelText('demand.expectedResult *'), { target: { value: '  Export exceptions  ' } })
    expect((screen.getByRole('button', { name: 'demand.save' }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText('demand.title *'), { target: { value: '  Order exceptions  ' } })
    fireEvent.click(screen.getByRole('button', { name: 'demand.save' }))
    expect(save).toHaveBeenCalledWith({ title: 'Order exceptions', scenario: 'Reconcile orders', expectedResult: 'Export exceptions', category: '', frequency: '', currentTimeCost: '', usageScope: '' })
  })

  it('keeps a draft when a save finishes unsuccessfully and prevents duplicate submission while pending', () => {
    const save = vi.fn()
    const view = render(<DemandForm pending={false} onSave={save} onCancel={vi.fn()} />)
    fireEvent.change(screen.getByLabelText('demand.title *'), { target: { value: 'Keep my draft' } })
    view.rerender(<DemandForm pending onSave={save} onCancel={vi.fn()} />)
    expect((screen.getByLabelText('demand.title *') as HTMLInputElement).disabled).toBe(true)
    view.rerender(<DemandForm pending={false} onSave={save} onCancel={vi.fn()} />)
    expect((screen.getByLabelText('demand.title *') as HTMLInputElement).value).toBe('Keep my draft')
  })
})
