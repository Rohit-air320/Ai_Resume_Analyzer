import { Link } from 'react-router-dom'
import { ArrowRight, Briefcase, FileText, Sparkles, TrendingUp } from 'lucide-react'
import PageHeader from '../components/layout/PageHeader.jsx'
import ScoreTrendChart from '../components/charts/ScoreTrendChart.jsx'
import ScorePill from '../components/score/ScorePill.jsx'
import EmptyState from '../components/state/EmptyState.jsx'
import ErrorState from '../components/state/ErrorState.jsx'
import { SkeletonDashboard } from '../components/state/Skeleton.jsx'
import { useAuth } from '../features/auth/authContext.js'
import { fetchDashboard } from '../features/dashboard/dashboardApi.js'
import { count, formatRelative } from '../lib/format.js'
import { useResource } from '../lib/useResource.js'

/**
 * The signed-in home screen.
 *
 * **One request.** `/api/dashboard` returns this page's shape — counts, three scores, the
 * trend, recent runs and the top gaps — because the alternative is five requests whose
 * slowest member decides how fast the page feels, and a client that has to know which
 * five. The endpoint is screen-shaped on purpose, and it is documented as such.
 *
 * **The trend is a real chart here, and only here.** Whether the line is going up is the
 * one question on this page whose answer is a shape rather than a number, which is the test
 * a chart has to pass to earn its place. It renders through `ChartFrame`, so the same three
 * columns exist as a table for anyone not looking at pictures.
 *
 * **Absent is not zero.** `scores` omits its fields entirely until there is an analysis to
 * average, so the cards render a dash rather than a confident 0 — a fabricated score is
 * worse than an empty one, and this whole product is an argument for numbers that mean
 * something.
 */

function Metric({ label, value, hint }) {
  return (
    <div className="card p-5">
      <p className="eyebrow">{label}</p>
      <p data-numeric="" className="mt-3 text-metric leading-none text-ink">
        {value ?? '—'}
      </p>
      {hint ? <p className="mt-2.5 text-xs text-ink-subtle">{hint}</p> : null}
    </div>
  )
}

export default function Dashboard() {
  const { user } = useAuth()
  const dashboard = useResource(() => fetchDashboard(), [])

  if (dashboard.isLoading) return <SkeletonDashboard />
  if (dashboard.hasFailed) {
    return (
      <ErrorState
        title="We could not load your dashboard"
        error={dashboard.error}
        onRetry={dashboard.reload}
      />
    )
  }

  const { counts, scores, scoreHistory = [], recentAnalyses = [], topSkillGaps = [], targetRole } =
    dashboard.data
  const firstName = user?.fullName?.split(' ')[0]
  const busiestGap = topSkillGaps[0]?.occurrences ?? 1

  return (
    <>
      <PageHeader
        eyebrow={targetRole ? `Aiming at ${targetRole}` : 'Dashboard'}
        title={firstName ? `Welcome back, ${firstName}` : 'Welcome back'}
        lead={
          counts.analyses > 0
            ? 'Every score here was calculated when the analysis ran, so the trend is a real history rather than a re-scoring of today.'
            : 'Upload a resume, paste the job you are applying to, and see how the two compare.'
        }
      >
        <Link to="/analyses/new" className="btn btn-primary">
          <Sparkles size={15} aria-hidden="true" />
          New analysis
        </Link>
      </PageHeader>

      {counts.analyses === 0 ? (
        <EmptyState
          icon={Sparkles}
          title="No analyses yet"
          detail="One resume and one job description is all it takes. You will get an ATS score, a match score, the skills you are missing, and specific edits to make."
          actionTo="/analyses/new"
          actionLabel="Run your first analysis"
        />
      ) : (
        <div className="space-y-6">
          <section aria-label="Your scores" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <Metric label="Latest score" value={scores.latest} hint="Your most recent analysis" />
            <Metric label="Best score" value={scores.best} hint="Across every run" />
            <Metric label="Average" value={scores.average} hint="All analyses, unweighted" />
            <Metric
              label="Analyses"
              value={counts.analyses}
              hint={`${count(counts.resumes, 'resume')} · ${count(counts.jobDescriptions, 'posting')}`}
            />
          </section>

          <div className="grid gap-6 xl:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)]">
            <section aria-labelledby="trend-heading" className="panel p-5 sm:p-7">
              <div className="flex items-center justify-between gap-4">
                <div>
                  <h2 id="trend-heading" className="text-base font-semibold">
                    Score history
                  </h2>
                  <p className="mt-1.5 text-sm text-ink-muted">
                    {scoreHistory.length === 0
                      ? 'Your completed analyses appear here.'
                      : `Your last ${scoreHistory.length === 1 ? 'analysis' : `${scoreHistory.length} analyses`}, oldest on the left.`}
                  </p>
                </div>
                <TrendingUp size={18} className="shrink-0 text-ink-subtle" aria-hidden="true" />
              </div>

              <div className="mt-7">
                {scoreHistory.length > 0 ? (
                  <ScoreTrendChart points={scoreHistory} captionHidden />
                ) : (
                  <p className="text-sm text-ink-muted">
                    Nothing to plot yet. A run that did not finish is counted but has no scores, so
                    the history stays empty until one completes.
                  </p>
                )}
              </div>
            </section>

            <section aria-labelledby="gaps-heading" className="panel p-5 sm:p-7">
              <h2 id="gaps-heading" className="text-base font-semibold">
                Skills you keep missing
              </h2>
              <p className="mt-1.5 text-sm text-ink-muted">
                Counted across every analysis. A skill that comes up repeatedly is the one worth
                learning next.
              </p>

              <ul className="mt-6 space-y-3.5">
                {topSkillGaps.length > 0 ? (
                  topSkillGaps.map((gap) => (
                    <li key={gap.skill}>
                      <div className="flex items-baseline justify-between gap-3">
                        <span className="truncate text-sm text-ink">{gap.skill}</span>
                        <span data-numeric="" className="text-xs text-ink-subtle">
                          {gap.occurrences}×
                        </span>
                      </div>
                      <div
                        aria-hidden="true"
                        className="mt-2 h-1 w-full overflow-hidden rounded-full bg-surface-sunken"
                      >
                        <span
                          className="block h-full rounded-full bg-accent-500"
                          style={{ width: `${Math.round((gap.occurrences / busiestGap) * 100)}%` }}
                        />
                      </div>
                    </li>
                  ))
                ) : (
                  <li className="text-sm text-ink-muted">
                    No repeated gaps — your resume covered what these postings asked for.
                  </li>
                )}
              </ul>

              {topSkillGaps.length > 0 ? (
                <Link
                  to="/skill-gap"
                  className="btn btn-ghost mt-5 -ml-2 text-sm text-brand-700"
                >
                  Break these down
                  <ArrowRight size={15} aria-hidden="true" />
                </Link>
              ) : null}
            </section>
          </div>

          <section aria-labelledby="recent-heading" className="panel p-5 sm:p-7">
            <div className="flex items-center justify-between gap-4">
              <h2 id="recent-heading" className="text-base font-semibold">
                Recent analyses
              </h2>
              <Link to="/analyses" className="btn btn-ghost text-sm">
                View all
                <ArrowRight size={15} aria-hidden="true" />
              </Link>
            </div>

            <ul className="mt-5 divide-y divide-line">
              {recentAnalyses.length === 0 ? (
                <li className="text-sm text-ink-muted">
                  Nothing completed yet.{' '}
                  <Link to="/analyses/new" className="font-medium text-brand-700 hover:underline">
                    Run an analysis
                  </Link>
                  .
                </li>
              ) : null}

              {recentAnalyses.map((analysis) => (
                <li key={analysis.id} className="py-3.5 first:pt-0 last:pb-0">
                  <Link
                    to={`/analyses/${analysis.id}`}
                    className="flex flex-wrap items-center justify-between gap-3 rounded-lg hover:text-brand-700"
                  >
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium text-ink">
                        {analysis.jobTitle}
                        {analysis.company ? (
                          <span className="font-normal text-ink-muted"> · {analysis.company}</span>
                        ) : null}
                      </span>
                      <span className="mt-1 block truncate text-xs text-ink-subtle">
                        {analysis.resumeLabel} · {formatRelative(analysis.createdAt)}
                      </span>
                    </span>

                    {analysis.status === 'FAILED' ? (
                      <span className="chip text-warning-600">Did not finish</span>
                    ) : (
                      <ScorePill score={analysis.overallScore} />
                    )}
                  </Link>
                </li>
              ))}
            </ul>
          </section>

          <section aria-label="Your library" className="grid gap-4 sm:grid-cols-2">
            <Link to="/resumes" className="card flex items-center gap-4 p-5 hover:border-line-strong">
              <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-brand-600/10 text-brand-600">
                <FileText size={17} aria-hidden="true" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-semibold text-ink">Your resumes</span>
                <span className="mt-1 block text-xs text-ink-subtle">
                  {count(counts.resumes, 'version')} uploaded
                </span>
              </span>
              <ArrowRight size={16} className="shrink-0 text-ink-subtle" aria-hidden="true" />
            </Link>

            <Link to="/analyses/new" className="card flex items-center gap-4 p-5 hover:border-line-strong">
              <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-accent-500/10 text-accent-600">
                <Briefcase size={17} aria-hidden="true" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-semibold text-ink">Job descriptions</span>
                <span className="mt-1 block text-xs text-ink-subtle">
                  {count(counts.jobDescriptions, 'posting')} saved
                </span>
              </span>
              <ArrowRight size={16} className="shrink-0 text-ink-subtle" aria-hidden="true" />
            </Link>
          </section>
        </div>
      )}
    </>
  )
}
