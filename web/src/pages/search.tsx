import { startTransition, useEffect, useRef, useState } from 'react'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Loader2 } from 'lucide-react'
import type { LabelCategory, ResourceType, SkillSummary } from '@/api/types'
import { useAuth } from '@/features/auth/use-auth'
import { SearchBar } from '@/features/search/search-bar'
import { LabelFilterGroups } from '@/features/search/label-filter-groups'
import { SkillCard } from '@/features/skill/skill-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { useVisibleLabels } from '@/shared/hooks/use-label-queries'
import { useMyStars } from '@/shared/hooks/use-user-queries'
import { toRouterPath } from '@/shared/lib/base-path'
import { formatNamespaceSearchInput, normalizeSearchQuery, parseNamespaceSearchInput } from '@/shared/lib/search-query'
import { Button } from '@/shared/ui/button'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'

const PAGE_SIZE = 12
const RESOURCE_TYPES: Array<{ value: ResourceType | undefined; label: string }> = [
  { value: undefined, label: 'search.allResourceTypes' },
  { value: 'SKILL', label: 'search.skillType' },
  { value: 'WEB', label: 'webResource.type' },
  { value: 'PLUGIN', label: 'pluginResource.type' },
  { value: 'PROMPT', label: 'promptResource.type' },
]

function blurActiveElement() {
  if (typeof document === 'undefined' || typeof HTMLElement === 'undefined') {
    return
  }

  if (document.activeElement instanceof HTMLElement) {
    document.activeElement.blur()
  }
}

function scrollToTopOnPageChange() {
  if (typeof window === 'undefined') {
    return () => {}
  }

  let secondFrame = 0
  const firstFrame = window.requestAnimationFrame(() => {
    window.scrollTo({ top: 0, behavior: 'auto' })
    secondFrame = window.requestAnimationFrame(() => {
      window.scrollTo({ top: 0, behavior: 'auto' })
    })
  })

  return () => {
    window.cancelAnimationFrame(firstFrame)
    if (secondFrame) {
      window.cancelAnimationFrame(secondFrame)
    }
  }
}

/**
 * Skill discovery page with synchronized URL state.
 *
 * Search text, sorting, pagination, and the starred-only filter are mirrored into router search
 * params so the page can be shared, restored, and revisited without losing state.
 */
function filterStarredSkills(skills: SkillSummary[], query: string, namespace: string, resourceType?: ResourceType, labelSlugs: string[] = []): SkillSummary[] {
  const normalizedQuery = query.trim().toLowerCase()
  const normalizedNamespace = namespace.trim().toLowerCase()

  return skills.filter((skill) => {
    if (!labelSlugs.every((slug) => skill.labels?.some((label) => label.slug === slug))) {
      return false
    }
    if (resourceType && (skill.resourceType ?? 'SKILL') !== resourceType) {
      return false
    }
    const matchesNamespace = !normalizedNamespace || skill.namespace.toLowerCase() === normalizedNamespace
    if (!matchesNamespace) {
      return false
    }
    if (!normalizedQuery) {
      return true
    }
    return [skill.displayName, skill.summary, skill.namespace, skill.slug]
        .filter(Boolean)
        .some((value) => value!.toLowerCase().includes(normalizedQuery))
  })
}

function sortStarredSkills(skills: SkillSummary[], sort: string): SkillSummary[] {
  const sorted = [...skills]
  if (sort === 'downloads') {
    return sorted.sort((left, right) => right.downloadCount - left.downloadCount)
  }
  if (sort === 'newest' || sort === 'relevance') {
    return sorted.sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime())
  }
  return sorted
}

export function SearchPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const searchParams = useSearch({ from: '/search' })
  const { isAuthenticated } = useAuth()

  const q = normalizeSearchQuery(searchParams.q || '')
  const namespace = (searchParams.namespace || '').replace(/^@/, '')
  const { data: labels } = useVisibleLabels()
  // Preserve old shared links while moving a known label into its business group.
  const legacyLabel = searchParams.label || ''
  const legacyCategory = labels?.find((label) => label.slug === legacyLabel)?.category ?? 'GENERAL'
  const selectedLabel = legacyCategory === 'GENERAL' ? legacyLabel : ''
  const selectedWorkflow = searchParams.workflow || (legacyCategory === 'WORKFLOW' ? legacyLabel : '')
  const selectedRole = searchParams.role || (legacyCategory === 'ROLE' ? legacyLabel : '')
  const resourceType = RESOURCE_TYPES.some((type) => type.value === searchParams.resourceType)
    ? searchParams.resourceType : undefined
  const sort = searchParams.sort || 'newest'
  const page = searchParams.page ?? 0
  const starredOnly = searchParams.starredOnly ?? false
  const [queryInput, setQueryInput] = useState(formatNamespaceSearchInput(namespace, q))
  const previousPageRef = useRef(page)

  useEffect(() => {
    setQueryInput(formatNamespaceSearchInput(namespace, q))
  }, [namespace, q])

  useEffect(() => {
    if (previousPageRef.current !== page) {
      blurActiveElement()
      const cleanupScroll = scrollToTopOnPageChange()

      previousPageRef.current = page
      return () => {
        cleanupScroll()
      }
    }

    previousPageRef.current = page
  }, [page])

  const { data, isLoading, isFetching } = useSearchSkills({
    resourceType,
    q,
    namespace: namespace || undefined,
    label: selectedLabel || undefined,
    workflow: selectedWorkflow || undefined,
    role: selectedRole || undefined,
    sort,
    page,
    size: PAGE_SIZE,
    starredOnly,
  }, !starredOnly)
  const {
    data: starredSkills,
    isLoading: isLoadingStarred,
    isFetching: isFetchingStarred,
  } = useMyStars(starredOnly && isAuthenticated)
  useEffect(() => {
    // Debounce URL updates while the user is typing so query state stays shareable without
    // triggering a navigation on every keystroke.
    const parsedInput = parseNamespaceSearchInput(queryInput)
    if (parsedInput.query === q && parsedInput.namespace === namespace) {
      return
    }

    if (!parsedInput.query && !parsedInput.namespace) {
      startTransition(() => {
        navigate({ to: '/search', search: { q: '', namespace: '', label: selectedLabel, workflow: selectedWorkflow || undefined, role: selectedRole || undefined, resourceType, sort, page: 0, starredOnly }, replace: page === 0 })
      })
      return
    }

    const timeoutId = window.setTimeout(() => {
      startTransition(() => {
        navigate({ to: '/search', search: { q: parsedInput.query, namespace: parsedInput.namespace, label: selectedLabel, workflow: selectedWorkflow || undefined, role: selectedRole || undefined, resourceType, sort, page: 0, starredOnly }, replace: true })
      })
    }, 250)

    return () => window.clearTimeout(timeoutId)
  }, [navigate, namespace, page, q, queryInput, selectedLabel, selectedWorkflow, selectedRole, resourceType, sort, starredOnly])

  const handleSearch = (query: string) => {
    const parsedInput = parseNamespaceSearchInput(query)
    setQueryInput(query)
    startTransition(() => {
      navigate({ to: '/search', search: { q: parsedInput.query, namespace: parsedInput.namespace, label: selectedLabel, workflow: selectedWorkflow || undefined, role: selectedRole || undefined, resourceType, sort, page: 0, starredOnly }, replace: true })
    })
  }

  const handleSortChange = (newSort: string) => {
    navigate({ to: '/search', search: { q, namespace, label: selectedLabel, workflow: selectedWorkflow || undefined, role: selectedRole || undefined, resourceType, sort: newSort, page: 0, starredOnly } })
  }

  const handlePageChange = (newPage: number) => {
    blurActiveElement()
    navigate({ to: '/search', search: { q, namespace, label: selectedLabel, workflow: selectedWorkflow || undefined, role: selectedRole || undefined, resourceType, sort, page: newPage, starredOnly } })
  }

  const handleLabelToggle = (category: LabelCategory, slug: string) => {
    const current = category === 'WORKFLOW' ? selectedWorkflow : category === 'ROLE' ? selectedRole : selectedLabel
    const next = current === slug ? '' : slug
    navigate({ to: '/search', search: {
      q, namespace, resourceType, sort, page: 0, starredOnly,
      label: category === 'GENERAL' ? next : selectedLabel,
      workflow: (category === 'WORKFLOW' ? next : selectedWorkflow) || undefined,
      role: (category === 'ROLE' ? next : selectedRole) || undefined,
    } })
  }

  const handleClearLabels = () => {
    navigate({ to: '/search', search: { q, namespace, resourceType, sort, page: 0, starredOnly } })
  }

  const handleNamespaceClear = () => {
    navigate({ to: '/search', search: { q, namespace: '', label: selectedLabel, workflow: selectedWorkflow || undefined, role: selectedRole || undefined, resourceType, sort, page: 0, starredOnly } })
  }

  const handleStarredToggle = () => {
    if (!isAuthenticated) {
      navigate({
        to: '/login',
        search: {
          returnTo: toRouterPath(window.location.pathname, window.location.search, window.location.hash),
        },
      })
      return
    }

    navigate({ to: '/search', search: { q, namespace, label: selectedLabel, workflow: selectedWorkflow || undefined, role: selectedRole || undefined, resourceType, sort, page: 0, starredOnly: !starredOnly } })
  }

  const handleSkillClick = (namespace: string, slug: string) => {
    navigate({
      to: `/space/${namespace}/${encodeURIComponent(slug)}`,
      search: { returnTo: toRouterPath(window.location.pathname, window.location.search) },
    })
  }

  const filteredStarredSkills = starredOnly
    ? sortStarredSkills(filterStarredSkills(starredSkills ?? [], q, namespace, resourceType, [selectedLabel, selectedWorkflow, selectedRole].filter(Boolean)), sort)
    : []
  const starredPageItems = starredOnly
    ? filteredStarredSkills.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)
    : []
  const totalPages = starredOnly
    ? Math.ceil(filteredStarredSkills.length / PAGE_SIZE)
    : data
      ? Math.ceil(data.total / data.size)
      : 0
  const displayItems = starredOnly
    ? starredPageItems
    : (data?.items ?? [])
  const isPageLoading = starredOnly ? isLoadingStarred : isLoading
  const isUpdatingResults = starredOnly
    ? isFetchingStarred && !isLoadingStarred
    : isFetching && !isLoading
  const resultCount = starredOnly
    ? filteredStarredSkills.length
    : (data?.total ?? 0)

  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME}>
      {/* Search Bar */}
      <div className="max-w-3xl mx-auto">
        <SearchBar
          value={queryInput}
          isSearching={isUpdatingResults}
          onChange={setQueryInput}
          onSearch={handleSearch}
        />
      </div>

      {/* Sort And Filters */}
      <div className="space-y-4">
        <div className="flex flex-wrap items-center gap-2" role="group" aria-label={t('search.resourceType')}>
          <span className="shrink-0 text-sm font-medium text-muted-foreground">{t('search.resourceType')}</span>
          {RESOURCE_TYPES.map((type) => (
            <Button
              key={type.value ?? 'all'}
              variant={resourceType === type.value ? 'default' : 'outline'}
              size="sm"
              className="whitespace-nowrap"
              aria-pressed={resourceType === type.value}
              onClick={() => navigate({ to: '/search', search: { q, namespace, label: selectedLabel, workflow: selectedWorkflow || undefined, role: selectedRole || undefined, resourceType: type.value, sort, page: 0, starredOnly } })}
            >
              {t(type.label)}
            </Button>
          ))}
        </div>
        <div className="flex items-center justify-between flex-wrap gap-4">
          <div className="flex min-w-0 flex-wrap items-center gap-3">
            <span className="text-sm font-medium text-muted-foreground">{t('search.sort.label')}</span>
            <div className="flex max-w-full flex-wrap gap-2">
              <Button
                variant={sort === 'relevance' ? 'default' : 'outline'}
                size="sm"
                onClick={() => handleSortChange('relevance')}
              >
                {t('search.sort.relevance')}
              </Button>
              <Button
                variant={sort === 'downloads' ? 'default' : 'outline'}
                size="sm"
                onClick={() => handleSortChange('downloads')}
              >
                {t('search.sort.downloads')}
              </Button>
              <Button
                variant={sort === 'newest' ? 'default' : 'outline'}
                size="sm"
                onClick={() => handleSortChange('newest')}
              >
                {t('search.sort.newest')}
              </Button>
            </div>
          </div>

          {resultCount > 0 && (
            <div className="text-sm text-muted-foreground">
              {t('search.results', { count: resultCount })}
            </div>
          )}
        </div>

        {isUpdatingResults ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" />
            <span>{t('search.loadingMore')}</span>
          </div>
        ) : null}

        <LabelFilterGroups
          labels={labels ?? []}
          selected={{ WORKFLOW: selectedWorkflow, ROLE: selectedRole, GENERAL: selectedLabel }}
          onSelect={handleLabelToggle}
        />

        <div className="flex flex-wrap items-center gap-2">
          <span className="shrink-0 text-sm font-medium text-muted-foreground">{t('search.otherFilters')}</span>
          <Button
            variant={starredOnly ? 'default' : 'outline'}
            size="sm"
            aria-pressed={starredOnly}
            onClick={handleStarredToggle}
          >
            {t('search.filterStarred')}
          </Button>
          {namespace ? (
            <Button
              variant="default"
              size="sm"
              onClick={handleNamespaceClear}
            >
              {t('search.namespaceFilter', { namespace })}
            </Button>
          ) : null}
        </div>
      </div>

      {/* Results */}
      {isPageLoading ? (
        <SkeletonList count={PAGE_SIZE} />
      ) : displayItems.length > 0 ? (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {displayItems.map((skill, idx) => (
              <div key={skill.id} className={`h-full animate-fade-up delay-${Math.min(idx % 6 + 1, 6)}`}>
                <SkillCard
                  skill={skill}
                  highlightStarred
                  onClick={() => handleSkillClick(skill.namespace, skill.slug)}
                />
              </div>
            ))}
          </div>
          {totalPages > 1 && (
            <Pagination
              page={page}
              totalPages={totalPages}
              onPageChange={handlePageChange}
            />
          )}
        </>
      ) : (
        <div className="space-y-4 text-center">
          <EmptyState
            title={starredOnly ? t('search.noStarredResults') : t('search.noResults')}
            description={
              starredOnly
                ? (q ? t('search.noStarredResultsFor', { q }) : t('search.noStarredSkills'))
                : (q ? t('search.noResultsFor', { q }) : undefined)
            }
          />
          {(selectedLabel || selectedWorkflow || selectedRole) && (
            <Button variant="outline" onClick={handleClearLabels}>{t('search.clearLabels')}</Button>
          )}
        </div>
      )}
    </div>
  )
}
