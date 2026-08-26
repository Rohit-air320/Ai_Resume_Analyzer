import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowUpRight, Lightbulb } from 'lucide-react'
import PageHeader from '../components/layout/PageHeader.jsx'
import EmptyState from '../components/state/EmptyState.jsx'
import ErrorState from '../components/state/ErrorState.jsx'
import { SkeletonList } from '../components/state/Skeleton.jsx'
import { listRecommendations } from '../features/recommendations/recommendationApi.js'
import { formatRelative, humanise } from '../lib/format.js'
import { useResource } from '../lib/useResource.js'

/**
 * Everything the analyses have suggested, in one place.
 *
 * **Why this page exists separately from a result.** "What should I learn next" is not a
 * question about one job posting. Advice produced against six postings is worth reading as
 * a set — the topic that came up four times is the one to act on — and that reading is
 * impossible on a screen that only ever shows one analysis at a time.
 *
 * **Filtering is a request, not a client-side filter.** The endpoint caps at 100 rows, so
 * filtering the fetched page in the browser would quietly hide the 40 keyword suggestions
 * that fell off the end of a list dominated by improvements. Asking the server for one kind
 * costs a round trip and returns the real answer.
 *
 * **Every item keeps its origin.** The job title and a link back to the analysis travel
 * with each row, because advice detached from the comparison that produced it is just an
 * opinion. "Learn Docker" means something once you can see which posting asked for it.
 *
 * **Server order is kept.** Newest first, not sorted by priority: a HIGH from March is not
 * more urgent than a MEDIUM from the job you applied to yesterday, and re-ranking would
 * hide that the list is a history.
 */

const FILTERS = [
  { value: null, label: 'Everything' },
  { value: 'IMPROVEMENT', label: 'Resume edits' },
  { value: 'LEARNING', label: 'Learn' },
  { value: 'PROJECT', label: 'Build' },
  { value: 'KEYWORD', label: 'Keywords' },
]

const PRIORITY_STYLES = {
  HIGH: 'text-danger-600',
  MEDIUM: 'text-warning-600',
  LOW: 'text-ink-subtle',
}

/**
 * Pressed buttons rather than `role="tab"`. A real tablist owes the user arrow-key
 * navigation and a matching tabpanel; toggle buttons owe nothing and already announce
 * their state through `aria-pressed`. Choosing the pattern you can honour beats claiming
 * one you cannot.
 */
function FilterBar({ active, onChange }) {
  return (
    <div role="group" aria-label="Filter recommendations by kind" className="flex flex-wrap gap-2">
      {FILTERS.map((filter) => {
        const pressed = filter.value === active

        return (
          <button
            key={filter.label}
            type="button"
            aria-pressed={pressed}
            onClick={() => onChange(filter.value)}
            className={
              pressed
                ? 'chip border-brand-600 bg-brand-600 text-ink-inverse'
                : 'chip text-ink-muted hover:border-line-strong hover:text-ink'
            }
          >
            {filter.label}
          </button>
        )
      })}
    </div>
  )
}

export default function Recommendations() {
  const [type, setType] = useState(null)
  const recommendations = useResource(() => listRecommendations(type), [type])
  const items = recommendations.data ?? []

  return (
    <>
      <PageHeader
        eyebrow="Recommendations"
        title="What to do next"
        lead="Collected from every analysis you have run. Each one is tied to the posting that prompted it, and none of them asks you to claim something you have not done."
      />

      <div className="space-y-6">
        <FilterBar active={type} onChange={setType} />

        {recommendations.isLoading ? (
          <SkeletonList rows={4} label="Loading your recommendations" />
        ) : null}

        {recommendations.hasFailed ? (
          <ErrorState
            title="We could not load your recommendations"
            error={recommendations.error}
            onRetry={recommendations.reload}
          />
        ) : null}

        {recommendations.isReady && items.length === 0 ? (
          <EmptyState
            icon={Lightbulb}
            title={type ? 'Nothing of this kind yet' : 'No recommendations yet'}
            detail={
              type
                ? 'Your analyses have not produced advice of this kind. Try another filter, or run an analysis against a different posting.'
                : 'Run an analysis and the suggestions it produces — edits, things to learn, projects worth building — collect here.'
            }
            actionTo="/analyses/new"
            actionLabel="Run an analysis"
          />
        ) : null}

        <ul className="space-y-3">
          {items.map((item) => (
            <li key={`${item.analysisId}-${item.type}-${item.title}`} className="card p-5">
              <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-2">
                <h2 className="text-sm font-semibold text-ink">{item.title}</h2>
                <div className="flex items-center gap-2">
                  <span className="chip text-ink-subtle">{humanise(item.type)}</span>
                  <span className={`chip ${PRIORITY_STYLES[item.priority] ?? 'text-ink-subtle'}`}>
                    {humanise(item.priority)}
                  </span>
                </div>
              </div>

              <p className="mt-2.5 max-w-prose text-sm leading-relaxed text-ink-muted">
                {item.detail}
              </p>

              <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 border-t border-line pt-3.5 text-xs">
                <Link
                  to={`/analyses/${item.analysisId}`}
                  className="font-medium text-brand-700 hover:underline"
                >
                  {item.jobTitle ? `From ${item.jobTitle}` : 'Open the analysis'}
                </Link>

                <span className="text-ink-subtle">{formatRelative(item.createdAt)}</span>

                {item.resourceUrl ? (
                  <a
                    href={item.resourceUrl}
                    target="_blank"
                    rel="noreferrer noopener"
                    className="ml-auto inline-flex items-center gap-1 font-medium text-ink-muted hover:text-ink"
                  >
                    Where to learn it
                    <ArrowUpRight size={13} aria-hidden="true" />
                  </a>
                ) : null}
              </div>
            </li>
          ))}
        </ul>
      </div>
    </>
  )
}
