import { useTranslation } from 'react-i18next'
import type { LabelCategory, LabelItem } from '@/api/types'
import { Button } from '@/shared/ui/button'

type Props = {
  labels: LabelItem[]
  selected: Record<LabelCategory, string>
  onSelect: (category: LabelCategory, slug: string) => void
}

const CATEGORIES: LabelCategory[] = ['WORKFLOW', 'ROLE', 'GENERAL']

export function LabelFilterGroups({ labels, selected, onSelect }: Props) {
  const { t } = useTranslation()
  return (
    <div className="space-y-3">
      {CATEGORIES.map((category) => {
        const options = labels.filter((label) => (label.category ?? 'GENERAL') === category)
        const value = selected[category]
        if (category === 'GENERAL' && options.length === 0 && !value) return null
        // A removed/hidden label in a saved URL must remain visible and removable.
        const missingSelection = value && !options.some((label) => label.slug === value)
        return (
          <div key={category} className="flex flex-wrap items-center gap-2" role="group" data-label-category={category} aria-label={t('labelCategories.' + category)}>
            <span className="shrink-0 text-sm font-medium text-muted-foreground">{t('labelCategories.' + category)}</span>
            <Button size="sm" variant={!value ? 'default' : 'outline'} aria-pressed={!value} onClick={() => onSelect(category, '')}>
              {t('search.allLabels')}
            </Button>
            {options.map((label) => (
              <Button key={label.slug} size="sm" className="whitespace-nowrap" variant={value === label.slug ? 'default' : 'outline'}
                aria-pressed={value === label.slug} onClick={() => onSelect(category, label.slug)}>
                {label.displayName}
              </Button>
            ))}
            {missingSelection && (
              <Button size="sm" variant="default" aria-pressed onClick={() => onSelect(category, '')}>{value} ×</Button>
            )}
          </div>
        )
      })}
    </div>
  )
}
