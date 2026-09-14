import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Textarea } from '@/shared/ui/textarea'
import type { DemandInput } from './use-demands'

export const emptyDemand: DemandInput = { title: '', scenario: '', expectedResult: '', category: '', frequency: '', currentTimeCost: '', usageScope: '' }

export function DemandForm({ initial = emptyDemand, pending, onSave, onCancel }: {
  initial?: DemandInput; pending: boolean; onSave: (content: DemandInput) => void; onCancel: () => void
}) {
  const { t } = useTranslation()
  const [content, setContent] = useState(initial)
  function submit(event: FormEvent) {
    event.preventDefault()
    if (!content.title.trim() || !content.scenario.trim() || !content.expectedResult.trim()) return
    onSave(Object.fromEntries(Object.entries(content).map(([key, value]) => [key, value.trim()])) as DemandInput)
  }
  const fields = [
    { key: 'title', max: 120, required: true },
    { key: 'scenario', max: 4000, required: true, multiline: true },
    { key: 'expectedResult', max: 2000, required: true, multiline: true },
    { key: 'category', max: 80 },
    { key: 'frequency', max: 200 },
    { key: 'currentTimeCost', max: 200 },
    { key: 'usageScope', max: 500 },
  ] as const
  return (
    <form onSubmit={submit} className="space-y-4 rounded-xl border bg-card p-5">
      <p className="text-sm text-muted-foreground">{t('demand.formHint')}</p>
      {fields.map(field => {
        const props = {
          id: `demand-${field.key}`, value: content[field.key], maxLength: field.max,
          required: 'required' in field && field.required, disabled: pending,
          onChange: (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => setContent({ ...content, [field.key]: event.target.value }),
        }
        return <div key={field.key} className="space-y-1.5">
          <label htmlFor={props.id} className="text-sm font-medium">{t(`demand.${field.key}`)}{props.required ? ' *' : ` · ${t('demand.optional')}`}</label>
          {'multiline' in field ? <Textarea {...props} rows={4} /> : <Input {...props} />}
        </div>
      })}
      <div className="flex gap-2">
        <Button type="submit" disabled={pending || !content.title.trim() || !content.scenario.trim() || !content.expectedResult.trim()}>{t(pending ? 'demand.saving' : 'demand.save')}</Button>
        <Button type="button" variant="outline" disabled={pending} onClick={onCancel}>{t('demand.cancel')}</Button>
      </div>
    </form>
  )
}
