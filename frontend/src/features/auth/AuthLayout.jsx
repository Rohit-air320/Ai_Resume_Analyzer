import { Link } from 'react-router-dom'
import MatchRail from '../../components/analysis/MatchRail.jsx'
import { DEMO_ANALYSIS } from '../demo/demoAnalysis.js'
import useDocumentTitle from '../../lib/useDocumentTitle.js'

/**
 * Shared frame for the sign-in and sign-up screens.
 *
 * The left column is not decoration. It is the product's signature element — the match rail,
 * which lays a posting's requirements against the evidence a resume actually provides. Putting
 * it here means the first thing a visitor sees is what the tool does, in the tool's own
 * vocabulary, rather than a stock illustration or a gradient. It makes no request, so it costs
 * nothing on a screen whose only job is to get a password typed.
 *
 * It is the **real** `MatchRail` reading the **same fixture** as the landing page and the demo.
 * Until Phase 10 this file carried its own hand-written copy of the rail, with its own tone map
 * and its own six rows — a preview that could quietly stop resembling the thing it previewed.
 * One component and one fixture means the shop window cannot go stale.
 *
 * It is hidden below `lg`. On a phone the form is the whole point, and a preview
 * pushed above it would put the sign-in fields below the fold.
 *
 * @param {object} props
 * @param {string} props.eyebrow  small label above the heading
 * @param {string} props.title    heading, set in the display face
 * @param {string} props.subtitle one sentence under the heading
 * @param {import('react').ReactNode} props.children the form
 * @param {import('react').ReactNode} props.footer   the link to the other screen
 */
export default function AuthLayout({ eyebrow, title, subtitle, children, footer }) {
  useDocumentTitle(typeof title === 'string' ? title : null)

  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[minmax(0,1fr)_28rem]">
      <MatchRailPreview />

      <main id="main" className="flex min-h-screen flex-col justify-center border-line px-6 py-12 sm:px-10 lg:border-l">
        <div className="mx-auto w-full max-w-sm">
          <Link to="/" className="font-display text-lg font-semibold tracking-tight">
            Resume<span className="text-brand-600">IQ</span>
          </Link>

          <p className="eyebrow mt-10">{eyebrow}</p>
          <h1 className="mt-3 text-display-md">{title}</h1>
          <p className="mt-3 text-sm text-ink-muted">{subtitle}</p>

          <div className="mt-8">{children}</div>

          <p className="mt-8 text-sm text-ink-muted">{footer}</p>
        </div>
      </main>
    </div>
  )
}

/**
 * The rail, the numbers and the disclaimer all come out of `DEMO_ANALYSIS`, so this panel
 * cannot claim a score the demo does not show.
 */
function MatchRailPreview() {
  return (
    <aside className="hidden bg-surface-sunken px-12 py-16 lg:flex lg:flex-col lg:justify-center">
      <div className="mx-auto w-full max-w-lg">
        <p className="eyebrow">Match rail · {DEMO_ANALYSIS.target.jobTitle}</p>
        <p className="mt-4 max-w-md font-display text-2xl font-semibold leading-tight">
          Every requirement in the posting, next to what your resume actually proves.
        </p>

        <div className="mt-10">
          <MatchRail
            detected={DEMO_ANALYSIS.detectedSkills}
            missing={DEMO_ANALYSIS.missingSkills}
          />
        </div>

        <dl className="mt-12 flex gap-10 border-t border-line pt-6">
          <Metric label="ATS score" value={DEMO_ANALYSIS.atsScore} />
          <Metric label="Job match" value={DEMO_ANALYSIS.jobMatchScore} />
          <Metric label="Gaps found" value={DEMO_ANALYSIS.missingSkills.length} />
        </dl>

        <p className="mt-8 max-w-md text-xs text-ink-subtle">
          Sample analysis. Suggestions are always tied to evidence already in your resume —
          nothing here invents experience you do not have.
        </p>
      </div>
    </aside>
  )
}

function Metric({ label, value }) {
  return (
    <div>
      <dt className="eyebrow">{label}</dt>
      <dd data-numeric className="mt-1.5 text-2xl text-ink">
        {value}
      </dd>
    </div>
  )
}
