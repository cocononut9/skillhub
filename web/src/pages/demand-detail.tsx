import { useState } from 'react'
import { Link, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ThumbsUp } from 'lucide-react'
import { useAuth } from '@/features/auth/use-auth'
import { DemandForm } from '@/features/demand/demand-form'
import { useDemand, useDemandAction, useSupplements } from '@/features/demand/use-demands'
import { Button } from '@/shared/ui/button'
import { Textarea } from '@/shared/ui/textarea'
import { DemandPagination } from './demands'

export function DemandDetailPage() {
  const { demandId } = useParams({ from: '/demands/$demandId' })
  return <DemandDetail key={demandId} id={Number(demandId)} />
}

function DemandDetail({ id }: { id: number }) {
  const { t, i18n } = useTranslation()
  const { hasRole } = useAuth()
  const admin = hasRole('SUPER_ADMIN') || hasRole('SKILL_ADMIN')
  const query = useDemand(id)
  const action = useDemandAction()
  const [editing, setEditing] = useState(false)
  const [page, setPage] = useState(0)
  const [content, setContent] = useState('')
  const [editingSupplement, setEditingSupplement] = useState<number | null>(null)
  const [editContent, setEditContent] = useState('')
  const [deleting, setDeleting] = useState<number | null>(null)
  const supplements = useSupplements(id, page, query.isSuccess)
  const date = (value: string) => new Date(value).toLocaleString(i18n.language)
  if (!Number.isSafeInteger(id) || id < 1) return <p role="alert">{t('demand.unavailable')}</p>
  if (query.isPending) return <p role="status">{t('demand.loading')}</p>
  if (query.isError) return <div role="alert">{t('demand.unavailable')} <Link to="/demands">{t('demand.back')}</Link></div>
  const demand = query.data

  return <div className="mx-auto max-w-4xl space-y-6 py-6">
    <Link to="/demands" className="text-sm text-primary hover:underline">← {t('demand.back')}</Link>
    {action.isError && <p role="alert" className="text-sm text-destructive">{t('demand.saveError')}</p>}
    <section className="space-y-5 rounded-xl border bg-card p-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0"><h1 className="break-words text-2xl font-semibold">{demand.title}</h1>
          <p className="mt-2 text-sm text-muted-foreground">{demand.authorName} · <time dateTime={demand.createdAt}>{date(demand.createdAt)}</time></p>
          {demand.updatedAt !== demand.createdAt && <p className="mt-1 text-xs text-muted-foreground">{t('demand.updated')} {date(demand.updatedAt)}</p>}
        </div>
        <div className="flex flex-wrap gap-2">
          {demand.mine && !demand.hidden && <Button variant="outline" disabled={action.isPending} onClick={() => setEditing(!editing)}>{t('demand.edit')}</Button>}
          {admin && <Button variant="outline" disabled={action.isPending} onClick={() => action.mutate({ kind: 'visibility', id, hidden: !demand.hidden })}>{t(demand.hidden ? 'demand.restore' : 'demand.hide')}</Button>}
        </div>
      </div>
      {demand.hidden && <p className="rounded-md bg-secondary p-3 text-sm">{t('demand.hiddenHint')}</p>}
      {editing && !demand.hidden ? <DemandForm initial={{ title: demand.title, scenario: demand.scenario, expectedResult: demand.expectedResult, category: demand.category, frequency: demand.frequency, currentTimeCost: demand.currentTimeCost, usageScope: demand.usageScope }} pending={action.isPending} onCancel={() => setEditing(false)} onSave={value => action.mutate({ kind: 'edit', id, content: value }, { onSuccess: () => setEditing(false) })} /> : <>
        {(['scenario', 'expectedResult', 'category', 'frequency', 'currentTimeCost', 'usageScope'] as const).map(key => demand[key] && <div key={key}>
          <h2 className="text-sm font-semibold">{t(`demand.${key}`)}</h2><p className="mt-1 whitespace-pre-wrap break-words text-sm leading-7">{demand[key]}</p>
        </div>)}
      </>}
      <div className="border-t pt-4">
        <Button variant={demand.supported ? 'default' : 'outline'} disabled={action.isPending || demand.hidden} aria-pressed={demand.supported} onClick={() => action.mutate({ kind: 'support', id, supported: !demand.supported })}>
          <ThumbsUp className="mr-2 h-4 w-4" />{t(demand.supported ? 'demand.supported' : 'demand.support')} · {demand.supportCount}
        </Button>
        <p className="mt-2 text-xs text-muted-foreground">{t('demand.supportHint')}</p>
      </div>
    </section>
    <section className="space-y-4">
      <h2 className="text-xl font-semibold">{t('demand.supplements')}</h2>
      {!demand.hidden && <form className="space-y-3 rounded-xl border bg-card p-5" onSubmit={event => {
        event.preventDefault()
        if (!content.trim()) return
        action.mutate({ kind: 'supplement', id, content: content.trim() }, { onSuccess: () => {
          setContent('')
          setPage(Math.floor((supplements.data?.total ?? 0) / 20))
        } })
      }}>
        <label htmlFor="new-supplement" className="text-sm font-medium">{t('demand.supplementHint')}</label>
        <Textarea id="new-supplement" rows={4} maxLength={4000} required value={content} disabled={action.isPending} onChange={event => setContent(event.target.value)} />
        <Button type="submit" disabled={action.isPending || !content.trim()}>{t('demand.addSupplement')}</Button>
      </form>}
      {supplements.isPending ? <p role="status">{t('demand.loading')}</p> : supplements.isError ? <div role="alert">{t('demand.loadError')} <Button variant="outline" onClick={() => void supplements.refetch()}>{t('demand.retry')}</Button></div> : <>
        {supplements.data.items.length === 0 && <p className="py-6 text-sm text-muted-foreground">{t('demand.noSupplements')}</p>}
        {supplements.data.items.map(item => <article key={item.id} className="space-y-3 rounded-xl border bg-card p-5">
          <div className="flex flex-wrap justify-between gap-2 text-sm"><span className="font-medium">{item.authorName}</span><time className="text-muted-foreground" dateTime={item.createdAt}>{date(item.createdAt)}</time></div>
          {item.hidden && <p className="text-xs text-muted-foreground">{t('demand.hidden')}</p>}
          {editingSupplement === item.id ? <form className="space-y-2" onSubmit={event => {
            event.preventDefault()
            if (editContent.trim()) action.mutate({ kind: 'editSupplement', id, supplementId: item.id, content: editContent.trim() }, { onSuccess: () => setEditingSupplement(null) })
          }}>
            <Textarea aria-label={t('demand.supplements')} required maxLength={4000} rows={4} value={editContent} disabled={action.isPending} onChange={event => setEditContent(event.target.value)} />
            <Button type="submit" disabled={action.isPending || !editContent.trim()}>{t('demand.save')}</Button>
            <Button type="button" variant="ghost" disabled={action.isPending} onClick={() => setEditingSupplement(null)}>{t('demand.cancel')}</Button>
          </form> : <p className="whitespace-pre-wrap break-words text-sm leading-7">{item.content}</p>}
          {item.updatedAt !== item.createdAt && <p className="text-xs text-muted-foreground">{t('demand.updated')} {date(item.updatedAt)}</p>}
          <div className="flex flex-wrap gap-2">
            {item.mine && !demand.hidden && !item.hidden && <>
              <Button variant="ghost" disabled={action.isPending} onClick={() => { setEditingSupplement(item.id); setEditContent(item.content) }}>{t('demand.edit')}</Button>
              <Button variant="ghost" disabled={action.isPending} onClick={() => setDeleting(item.id)}>{t('demand.delete')}</Button>
            </>}
            {admin && <Button variant="ghost" disabled={action.isPending} onClick={() => action.mutate({ kind: 'visibility', id, supplementId: item.id, hidden: !item.hidden })}>{t(item.hidden ? 'demand.restore' : 'demand.hide')}</Button>}
          </div>
          {deleting === item.id && <div className="flex flex-wrap items-center gap-2 text-sm">
            <span>{t('demand.deleteConfirm')}</span>
            <Button variant="destructive" disabled={action.isPending} onClick={() => action.mutate({ kind: 'deleteSupplement', id, supplementId: item.id }, { onSuccess: () => { setDeleting(null); if (supplements.data.items.length === 1 && page > 0) setPage(page - 1) } })}>{t('demand.delete')}</Button>
            <Button variant="outline" disabled={action.isPending} onClick={() => setDeleting(null)}>{t('demand.cancel')}</Button>
          </div>}
        </article>)}
        <DemandPagination page={page} total={supplements.data.total} onChange={setPage} />
      </>}
    </section>
  </div>
}
