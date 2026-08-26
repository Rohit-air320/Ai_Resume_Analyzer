import { Link } from 'react-router-dom'
import { ChevronRight, History as HistoryIcon, Sparkles } from 'lucide-react'
import PageHeader from '../components/layout/PageHeader.jsx'
import ScorePill from '../components/score/ScorePill.jsx'
import ConfirmDelete from '../components/state/ConfirmDelete.jsx'
import EmptyState from '../components/state/EmptyState.jsx'
import ErrorState from '../components/state/ErrorState.jsx'
import { SkeletonList } from '../components/state/Skeleton.jsx'
import { deleteAnalysis, listAnalyses } from '../features/analyses/analysisApi.js'
import { formatRelative } from '../lib/format.js'
import { useResource } from '../lib/useResource.js'

/**
 * Every analysis, newest first.
 *
 * The three numbers on each row are overall, ATS and job match — enough to see the shape
 * of a run without opening it, and few enough that the row stays readable on a phone. The
 * rest of the document lives behind the link, because a history table that loaded skills
 * and advice for every row would fetch four child collections per row to render three
 * numbers.
 *
 * A failed run is kept and labelled rather than filtered out. It happened, it might have
 * happened for a reason worth knowing about, and a list that quietly drops rows is a list
 * nobody can trust.
 */
export default function History() {
  const analyses = useResource(() => listAnalyses(), [])
  const items = analyses.data ?? []

  async function remove(analysis) {
    await deleteAnalysis(analysis.id)
    analyses.setData(items.filter((item) => item.id !== analysis.id))
  }

  return (
    <>
      <PageHeader
        eyebrow="Analysis history"
        title="Every run, kept"
        lead="Scores are stored as they were calculated, so an older analysis still shows what your resume looked like then — which is how you see progress rather than guess at it."
      >
        <Link to="/analyses/new" className="btn btn-primary">
          <Sparkles size={15} aria-hidden="true" />
          New analysis
        </Link>
      </PageHeader>

      {analyses.isLoading ? <SkeletonList rows={4} label="Loading your analyses" /> : null}

      {analyses.hasFailed ? (
        <ErrorState
          title="We could not load your history"
          error={analyses.error}
          onRetry={analyses.reload}
        />
      ) : null}

      {analyses.isReady && items.length === 0 ? (
        <EmptyState
          icon={HistoryIcon}
          title="No analyses yet"
          detail="Run your first one and it will appear here, with the scores it was given at the time."
          actionTo="/analyses/new"
          actionLabel="Run an analysis"
        />
      ) : null}

      <ul className="space-y-3">
        {items.map((analysis) => {
          const failed = analysis.status === 'FAILED'

          return (
            <li key={analysis.id} className="card p-5">
              <div className="flex flex-wrap items-start gap-4">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <Link
                      to={`/analyses/${analysis.id}`}
                      className="truncate text-sm font-semibold text-ink hover:text-brand-700"
                    >
                      {analysis.jobTitle}
                      {analysis.company ? (
                        <span className="font-normal text-ink-muted"> · {analysis.company}</span>
                      ) : null}
                    </Link>
                    {failed ? <span className="chip text-warning-600">Did not finish</span> : null}
                  </div>

                  <p className="mt-1.5 truncate text-xs text-ink-subtle">
                    {analysis.resumeLabel} · {formatRelative(analysis.createdAt)}
                  </p>
                </div>

                {failed ? null : (
                  <div className="flex items-center gap-5">
                    <ScorePill score={analysis.overallScore} />

                    <dl className="hidden items-center gap-5 sm:flex">
                      <div className="text-right">
                        <dt className="eyebrow">ATS</dt>
                        <dd data-numeric="" className="mt-1 text-sm font-semibold text-ink">
                          {analysis.atsScore ?? '—'}
                        </dd>
                      </div>
                      <div className="text-right">
                        <dt className="eyebrow">Match</dt>
                        <dd data-numeric="" className="mt-1 text-sm font-semibold text-ink">
                          {analysis.jobMatchScore ?? '—'}
                        </dd>
                      </div>
                    </dl>
                  </div>
                )}

                <div className="flex items-center gap-1">
                  <ConfirmDelete
                    onConfirm={() => remove(analysis)}
                    label={`Delete the analysis for ${analysis.jobTitle}`}
                    question="Delete this analysis?"
                  />
                  <Link
                    to={`/analyses/${analysis.id}`}
                    aria-label={`Open the analysis for ${analysis.jobTitle}`}
                    className="btn btn-ghost px-2.5 py-2 text-ink-subtle"
                  >
                    <ChevronRight size={17} aria-hidden="true" />
                  </Link>
                </div>
              </div>
            </li>
          )
        })}
      </ul>
    </>
  )
}
