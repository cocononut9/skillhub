import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useVisibleLabels } from '@/shared/hooks/use-label-queries'
import { Input } from '@/shared/ui/input'
import { Button } from '@/shared/ui/button'

type PublishLabelPickerProps = {
  selected: string[]
  onChange: (slugs: string[]) => void
  disabled?: boolean
}

export function PublishLabelPicker({ selected, onChange, disabled }: PublishLabelPickerProps) {
  const { t } = useTranslation()
  const [query, setQuery] = useState('')
  const { data: labels, isLoading, isError, refetch } = useVisibleLabels()
  const keyword = query.trim().toLocaleLowerCase()
  const available = (labels ?? []).filter((label) => label.type !== 'PRIVILEGED')
  const filtered = available.filter((label) =>
    `${label.displayName} ${label.slug}`.toLocaleLowerCase().includes(keyword),
  )

  return (
    <fieldset className="space-y-3" disabled={disabled}>
      <legend className="text-sm font-semibold font-heading">{t('publish.labelsTitle')}</legend>
      <p className="text-sm text-muted-foreground">{t('publish.labelsHint')}</p>
      {isLoading ? (
        <p className="text-sm text-muted-foreground" role="status">{t('publish.labelsLoading')}</p>
      ) : isError ? (
        <div className="flex items-center gap-2 text-sm" role="alert">
          <span>{t('publish.labelsLoadError')}</span>
          <Button type="button" variant="outline" size="sm" onClick={() => void refetch()}>{t('publish.labelsRetry')}</Button>
        </div>
      ) : available.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t('publish.labelsEmpty')}</p>
      ) : (
        <>
          <Input
            aria-label={t('publish.labelsSearch')}
            placeholder={t('publish.labelsSearch')}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
          <div className="flex max-h-48 flex-wrap gap-2 overflow-y-auto p-1">
            {filtered.map((label) => (
              <label key={label.slug} className="flex cursor-pointer items-center gap-2 rounded-lg border border-border px-3 py-2 text-sm">
                <input
                  type="checkbox"
                  checked={selected.includes(label.slug)}
                  onChange={(event) => onChange(event.target.checked
                    ? [...selected, label.slug]
                    : selected.filter((slug) => slug !== label.slug))}
                />
                {label.displayName}
              </label>
            ))}
            {filtered.length === 0 && <p className="text-sm text-muted-foreground">{t('publish.labelsNoMatch')}</p>}
          </div>
          <p className="text-xs text-muted-foreground" role="status">{t('publish.labelsSelected', { count: selected.length })}</p>
        </>
      )}
    </fieldset>
  )
}
