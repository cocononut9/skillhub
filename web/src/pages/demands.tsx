import { useState } from 'react'
import { Link, useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { MessageSquare, Plus, ThumbsUp } from 'lucide-react'
import { useAuth } from '@/features/auth/use-auth'
import { DemandForm } from '@/features/demand/demand-form'
import { useDemands, useDemandAction, type DemandFilters } from '@/features/demand/use-demands'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'

export function DemandsPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const { hasRole } = useAuth()
  const admin = hasRole('SUPER_ADMIN') || hasRole('SKILL_ADMIN')
  const [filters, setFilters] = useState<DemandFilters>({ q: '', category: '', mine: false, includeHidden: false, sort: 'newest', page: 0 })
  const [q, setQ] = useState('')
  const [category, setCategory] = useState('')
  const [creating, setCreating] = useState(false)
  const query = useDemands(filters)
  const action = useDemandAction()
  function filter(values: Partial<DemandFilters>) { setFilters({ ...filters, ...values, page: 0 }) }

  return <div className="mx-auto max-w-5xl space-y-6 py-6">
    <div className="flex flex-wrap items-start justify-between gap-4">
      <div><h1 className="text-3xl font-semibold">{t('demand.square')}</h1><p className="mt-2 text-muted-foreground">{t('demand.intro')}</p></div>
      <Button onClick={() => { action.reset(); setCreating(true) }} disabled={creating}><Plus className="mr-2 h-4 w-4" />{t('demand.publish')}</Button>
    </div>
    {creating && <DemandForm pending={action.isPending} onCancel={() => setCreating(false)} onSave={content => action.mutate({ kind: 'create', content }, { onSuccess: id => {
      setCreating(false)
      if (typeof id === 'number') void navigate({ to: '/demands/$demandId', params: { demandId: String(id) } })
    } })} />}
    {action.isError && <p role="alert" className="text-sm text-destructive">{t('demand.saveError')}</p>}
    <div className="space-y-3 rounded-xl border bg-card p-4">
      <form className="flex flex-wrap gap-2" onSubmit={event => { event.preventDefault(); filter({ q, category }) }}>
        <Input className="min-w-48 flex-1" aria-label={t('demand.search')} placeholder={t('demand.search')} value={q} maxLength={120} onChange={event => setQ(event.target.value)} />
        <Input className="w-44" aria-label={t('demand.categoryFilter')} placeholder={t('demand.categoryFilter')} value={category} maxLength={80} onChange={event => setCategory(event.target.value)} />
        <Button type="submit" variant="outline">{t('demand.searchButton')}</Button>
      </form>
      <div className="flex flex-wrap items-center gap-4 text-sm">
        <select aria-label={t('demand.sort')} className="rounded-md border bg-background p-2" value={filters.sort} onChange={event => filter({ sort: event.target.value as DemandFilters['sort'] })}>
          <option value="newest">{t('demand.newest')}</option><option value="popular">{t('demand.popular')}</option>
        </select>
        <label className="flex items-center gap-2"><input type="checkbox" checked={filters.mine} onChange={event => filter({ mine: event.target.checked })} />{t('demand.mine')}</label>
        {admin && <label className="flex items-center gap-2"><input type="checkbox" checked={filters.includeHidden} onChange={event => filter({ includeHidden: event.target.checked })} />{t('demand.includeHidden')}</label>}
      </div>
    </div>
    {query.isPending ? <p role="status">{t('demand.loading')}</p> : query.isError ? <div role="alert">{t('demand.loadError')} <Button variant="outline" onClick={() => void query.refetch()}>{t('demand.retry')}</Button></div> : <>
      <p className="text-sm text-muted-foreground">{t('demand.total', { count: query.data.total })}</p>
      {query.data.items.length === 0 && <div className="rounded-xl border border-dashed p-12 text-center text-muted-foreground">{t('demand.empty')}</div>}
      <div className="grid gap-4">{query.data.items.map(demand => <article key={demand.id} className="rounded-xl border bg-card p-5 transition-shadow hover:shadow-sm">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0 flex-1">
            <Link to="/demands/$demandId" params={{ demandId: String(demand.id) }} className="break-words text-lg font-semibold hover:underline">{demand.title}</Link>
            {demand.hidden && <span className="ml-2 text-xs text-muted-foreground">{t('demand.hidden')}</span>}
            <p className="mt-2 line-clamp-2 whitespace-pre-wrap break-words text-sm text-muted-foreground">{demand.scenario}</p>
          </div>
          <Button variant={demand.supported ? 'default' : 'outline'} disabled={demand.hidden || action.isPending} aria-pressed={demand.supported} aria-label={`${t('demand.support')}: ${demand.title}`} onClick={() => action.mutate({ kind: 'support', id: demand.id, supported: !demand.supported })}>
            <ThumbsUp className="mr-2 h-4 w-4" /><span className="hidden sm:inline">{t(demand.supported ? 'demand.supported' : 'demand.support')}&nbsp;</span>{demand.supportCount}
          </Button>
        </div>
        <div className="mt-4 flex flex-wrap items-center gap-4 text-xs text-muted-foreground">
          <span>{demand.authorName}</span><time dateTime={demand.createdAt}>{new Date(demand.createdAt).toLocaleDateString(i18n.language)}</time>
          {demand.category && <span className="rounded-full bg-secondary px-2 py-1">{demand.category}</span>}
          <span className="flex items-center gap-1"><MessageSquare className="h-3.5 w-3.5" />{t('demand.supplementCount', { count: demand.supplementCount })}</span>
        </div>
      </article>)}</div>
      <DemandPagination page={filters.page} total={query.data.total} onChange={page => setFilters({ ...filters, page })} />
    </>}
  </div>
}

export function DemandPagination({ page, total, onChange }: { page: number; total: number; onChange: (page: number) => void }) {
  const { t } = useTranslation()
  if (total <= 20 && page === 0) return null
  return <div className="flex items-center justify-center gap-4">
    <Button variant="outline" disabled={page === 0} onClick={() => onChange(page - 1)}>{t('demand.previous')}</Button>
    <span className="text-sm">{t('demand.page', { page: page + 1, total: Math.max(1, Math.ceil(total / 20)) })}</span>
    <Button variant="outline" disabled={(page + 1) * 20 >= total} onClick={() => onChange(page + 1)}>{t('demand.next')}</Button>
  </div>
}
