import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchJson, getCsrfHeaders, WEB_API_PREFIX } from '@/api/client'
import type { components } from '@/api/generated/schema'

export type Demand = Required<components['schemas']['DemandResponse']>
export type Supplement = Required<components['schemas']['DemandSupplementResponse']>
export type DemandInput = Required<components['schemas']['DemandRequest']>
type DemandPage = Omit<Required<components['schemas']['DemandPageResponse']>, 'items'> & { items: Demand[] }
type SupplementPage = Omit<Required<components['schemas']['DemandSupplementPageResponse']>, 'items'> & { items: Supplement[] }
export type DemandFilters = { q: string; category: string; mine: boolean; includeHidden: boolean; sort: 'newest' | 'popular'; page: number }
const prefix = `${WEB_API_PREFIX}/demands`

export function useDemands(filters: DemandFilters) {
  const params = new URLSearchParams(Object.entries({ ...filters, size: 20 }).map(([key, value]) => [key, String(value)]))
  return useQuery({ queryKey: ['demands', 'list', filters], queryFn: () => fetchJson<DemandPage>(`${prefix}?${params}`) })
}

export function useDemand(id: number) {
  return useQuery({ queryKey: ['demands', id], queryFn: () => fetchJson<Demand>(`${prefix}/${id}`), enabled: Number.isSafeInteger(id) && id > 0 })
}

export function useSupplements(id: number, page: number, enabled: boolean) {
  return useQuery({ queryKey: ['demands', id, 'supplements', page], queryFn: () => fetchJson<SupplementPage>(`${prefix}/${id}/supplements?page=${page}&size=20`), enabled })
}

type DemandAction =
  | { kind: 'create'; content: DemandInput }
  | { kind: 'edit'; id: number; content: DemandInput }
  | { kind: 'support'; id: number; supported: boolean }
  | { kind: 'supplement'; id: number; content: string }
  | { kind: 'editSupplement'; id: number; supplementId: number; content: string }
  | { kind: 'deleteSupplement'; id: number; supplementId: number }
  | { kind: 'visibility'; id: number; supplementId?: number; hidden: boolean }

export async function mutateDemand(action: DemandAction): Promise<number | void> {
  let path = 'id' in action ? `/${action.id}` : ''
  let method = 'POST'
  let body: DemandInput | components['schemas']['DemandSupplementRequest'] | undefined
  switch (action.kind) {
    case 'create': body = action.content; break
    case 'edit': method = 'PUT'; body = action.content; break
    case 'support': path += '/support'; method = action.supported ? 'PUT' : 'DELETE'; break
    case 'supplement': path += '/supplements'; body = { content: action.content }; break
    case 'editSupplement': path += `/supplements/${action.supplementId}`; method = 'PUT'; body = { content: action.content }; break
    case 'deleteSupplement': path += `/supplements/${action.supplementId}`; method = 'DELETE'; break
    case 'visibility':
      path += `${action.supplementId === undefined ? '' : `/supplements/${action.supplementId}`}/visibility?hidden=${action.hidden}`
      method = 'PUT'
      break
  }
  return fetchJson<number | void>(`${prefix}${path}`, {
    method,
    headers: getCsrfHeaders(body ? { 'Content-Type': 'application/json' } : undefined),
    ...(body ? { body: JSON.stringify(body) } : {}),
  })
}

export function useDemandAction() {
  const client = useQueryClient()
  return useMutation({ mutationFn: mutateDemand, onSuccess: () => client.invalidateQueries({ queryKey: ['demands'] }) })
}
