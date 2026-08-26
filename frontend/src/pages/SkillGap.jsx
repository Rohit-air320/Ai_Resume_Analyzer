import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, Target } from 'lucide-react'
import PageHeader from '../components/layout/PageHeader.jsx'
import MatchRail from '../components/analysis/MatchRail.jsx'
import GapFrequencyChart from '../components/charts/GapFrequencyChart.jsx'
import SectionRadar from '../components/charts/SectionRadar.jsx'
import SkillCoverageChart from '../components/charts/SkillCoverageChart.jsx'
import EmptyState from '../components/state/EmptyState.jsx'
import ErrorState from '../components/state/ErrorState.jsx'
import { SkeletonDashboard } from '../components/state/Skeleton.jsx'
import { getAnalysis, listAnalyses } from '../features/analyses/analysisApi.js'
import { fetchDashboard } from '../features/dashboard/dashboardApi.js'
import { count, formatDate } from '../lib/format.js'
import { useResource } from '../lib/useResource.js'

/**
 * The gap, from two distances.
 *
 * **Across analyses, then inside one.** The chart at the top counts how many times each
 * requirement has come back missing, which is the only view in the product that can tell a
 * posting's quirk from a hole in the resume. Everything below it is one analysis in detail,
 * because "learn Docker" is advice and "this posting called Docker critical and your resume
 * never says it" is a reason.
 *
 * **The picker defaults to the newest completed run** and is a plain `<select>`. A list of
 * cards to choose from would take the height of the thing it is filtering, and a native
 * select already gets keyboard support, touch handling and a searchable dropdown on desktop
 * for free.
 *
 * **Failed runs are not in the picker.** A run that did not finish has no scores and no
 * skills, so offering it would offer a blank page.
 *
 * **Three requests, not one.** The frequency chart needs the account-wide roll-up from
 * `/api/dashboard`, the picker needs the history, and the detail needs one analysis. They
 * are genuinely three questions, and the third one changes as the user picks — which is
 * exactly the case a screen-shaped endpoint cannot serve.
 */
export default function SkillGap() {
  const dashboard = useResource(() => fetchDashboard(), [])
  const analyses = useResource(() => listAnalyses(), [])
  const [chosenId, setChosenId] = useState(null)

  const completed = (analyses.data ?? []).filter((item) => item.status !== 'FAILED')
  // `chosenId` stays null until the user picks, so the default follows the data rather
  // than being copied into state by an effect that then has to be kept in sync.
  const activeId = chosenId ?? completed[0]?.id ?? null

  const analysis = useResource(
    () => (activeId ? getAnalysis(activeId) : Promise.resolve(null)),
    [activeId],
  )

  if (dashboard.isLoading || analyses.isLoading) return <SkeletonDashboard />

  if (dashboard.hasFailed || analyses.hasFailed) {
    const failed = dashboard.hasFailed ? dashboard : analyses
    return (
      <ErrorState
        title="We could not load your skill gaps"
        error={failed.error}
        onRetry={failed.reload}
      />
    )
  }

  const gaps = dashboard.data?.topSkillGaps ?? []
  const result = analysis.data

  return (
    <>
      <PageHeader
        eyebrow="Skill gap"
        title="What your resume does not show yet"
        lead="Only requirements the postings actually named. Nothing here is a generic list of skills a developer ought to have."
      />

      {completed.length === 0 ? (
        <EmptyState
          icon={Target}
          title="No completed analyses yet"
          detail="Gaps are read out of a comparison, so there is nothing to show until one resume has been scored against one posting."
          actionTo="/analyses/new"
          actionLabel="Run an analysis"
        />
      ) : (
        <div className="space-y-6">
          <section aria-labelledby="frequency-heading" className="panel p-5 sm:p-7">
            <h2 id="frequency-heading" className="text-base font-semibold">
              Across every analysis
            </h2>
            <p className="mt-1.5 max-w-2xl text-sm text-ink-muted">
              {gaps.length > 0
                ? `Counted over ${count(completed.length, 'analysis', 'analyses')}. A requirement that keeps reappearing is worth more than one that showed up once.`
                : 'Nothing has come back missing more than once.'}
            </p>

            <div className="mt-6">
              {gaps.length > 0 ? (
                <GapFrequencyChart gaps={gaps} />
              ) : (
                <p className="text-sm text-ink-muted">
                  Your resume covered what these postings asked for. Run an analysis against a more
                  senior posting to find the next thing to learn.
                </p>
              )}
            </div>
          </section>

          <section aria-labelledby="picker-heading" className="panel p-5 sm:p-7">
            <div className="flex flex-wrap items-end justify-between gap-4">
              <div className="min-w-0">
                <h2 id="picker-heading" className="text-base font-semibold">
                  One analysis in detail
                </h2>
                <p className="mt-1.5 text-sm text-ink-muted">
                  Pick a run to see how that posting weighted what it asked for.
                </p>
              </div>

              <div className="w-full sm:w-72">
                <label className="field-label sr-only" htmlFor="analysis-picker">
                  Analysis
                </label>
                <select
                  id="analysis-picker"
                  className="field"
                  value={activeId ?? ''}
                  onChange={(event) => setChosenId(event.target.value)}
                >
                  {completed.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.jobTitle} · {formatDate(item.createdAt)}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {analysis.hasFailed ? (
              <div className="mt-6">
                <ErrorState
                  title="We could not load that analysis"
                  error={analysis.error}
                  onRetry={analysis.reload}
                />
              </div>
            ) : null}

            {analysis.isReady && result ? (
              <div className="mt-7 space-y-8">
                <SkillCoverageChart
                  detected={result.detectedSkills}
                  missing={result.missingSkills}
                />

                <div className="border-t border-line pt-7">
                  <h3 className="text-sm font-semibold text-ink">Unmet requirements</h3>
                  <p className="mt-1 text-xs text-ink-muted">
                    Grouped by how much this posting weighted them. A broken line is a requirement
                    with no evidence behind it.
                  </p>

                  <div className="mt-6">
                    {result.missingSkills?.length > 0 ? (
                      <MatchRail missing={result.missingSkills} />
                    ) : (
                      <p className="text-sm text-ink-muted">
                        Nothing missing — this resume covers every skill the posting names.
                      </p>
                    )}
                  </div>
                </div>

                {result.sectionScores?.length > 0 ? (
                  <div className="border-t border-line pt-7">
                    <SectionRadar sections={result.sectionScores} />
                  </div>
                ) : null}

                <div className="border-t border-line pt-5">
                  <Link
                    to={`/analyses/${result.id}`}
                    className="btn btn-ghost -ml-2 text-sm text-brand-700"
                  >
                    Open the full analysis
                    <ArrowRight size={15} aria-hidden="true" />
                  </Link>
                </div>
              </div>
            ) : null}
          </section>
        </div>
      )}
    </>
  )
}
