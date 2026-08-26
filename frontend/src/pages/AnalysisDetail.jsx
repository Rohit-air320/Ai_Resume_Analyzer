import { Link, useLocation, useParams } from 'react-router-dom'
import { ArrowLeft, Sparkles } from 'lucide-react'
import PageHeader from '../components/layout/PageHeader.jsx'
import AnalysisReport from '../components/analysis/AnalysisReport.jsx'
import ErrorState from '../components/state/ErrorState.jsx'
import { SkeletonDashboard } from '../components/state/Skeleton.jsx'
import { getAnalysis } from '../features/analyses/analysisApi.js'
import { formatDateTime } from '../lib/format.js'
import { useResource } from '../lib/useResource.js'

/**
 * One analysis, in full.
 *
 * **Reads the document the create call already returned.** Arriving from the wizard, the
 * analysis comes in via router state, so the page renders with no request at all; arriving
 * from a link or a refresh, it fetches. The API guarantees both endpoints return the same
 * document, which is what makes that shortcut safe rather than a source of drift.
 *
 * The report itself lives in `components/analysis/AnalysisReport.jsx` because the public demo
 * renders the same thing from a fixture. What is left here is the part only a signed-in page
 * needs: which analysis, how to fetch it, and what to show while it loads or if it fails.
 */
export default function AnalysisDetail() {
  const { id } = useParams()
  const location = useLocation()
  const handed = location.state?.analysis?.id === id ? location.state.analysis : null

  const analysis = useResource(() => (handed ? Promise.resolve(handed) : getAnalysis(id)), [id])

  if (analysis.isLoading) return <SkeletonDashboard />
  if (analysis.hasFailed) {
    return (
      <ErrorState
        title="We could not load this analysis"
        error={analysis.error}
        onRetry={analysis.reload}
      />
    )
  }

  const result = analysis.data
  const target = result.target ?? {}

  return (
    <>
      <Link to="/analyses" className="btn btn-ghost mb-4 -ml-2">
        <ArrowLeft size={15} aria-hidden="true" />
        All analyses
      </Link>

      <PageHeader
        eyebrow={target.company ? `${target.jobTitle} · ${target.company}` : target.jobTitle}
        title="Analysis"
        lead={`${target.resumeLabel} · analysed ${formatDateTime(result.completedAt ?? result.createdAt)}`}
      >
        <Link to="/analyses/new" className="btn btn-secondary">
          <Sparkles size={15} aria-hidden="true" />
          Run another
        </Link>
      </PageHeader>

      <AnalysisReport result={result} />
    </>
  )
}
