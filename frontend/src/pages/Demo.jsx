import { Link } from 'react-router-dom'
import { ArrowRight, FlaskConical } from 'lucide-react'
import SiteHeader from '../components/marketing/SiteHeader.jsx'
import SiteFooter from '../components/marketing/SiteFooter.jsx'
import AnalysisReport from '../components/analysis/AnalysisReport.jsx'
import { DEMO_ANALYSIS } from '../features/demo/demoAnalysis.js'
import useDocumentTitle from '../lib/useDocumentTitle.js'

/**
 * The full report, readable without an account.
 *
 * **This is the signed-in results page.** Not a screenshot of it, not a trimmed version of it —
 * the same `AnalysisReport` component, handed a fixture instead of a fetch. Anything a future
 * phase adds to the report appears here on the same commit, and the demo cannot quietly become
 * a description of an older product.
 *
 * **No request leaves the browser.** The spec asks for a demo with no signup, and shipping one
 * document is the honest way to do that: there is no anonymous endpoint to secure, no rate limit
 * to reason about, and no path by which somebody else's resume could ever be served here. It
 * also means the demo works with the backend switched off, which is the state a reviewer opening
 * this project for the first time is most likely to be in.
 *
 * The banner is not decoration either. A page that shows a plausible ATS score without saying
 * whose it is invites the reader to think it is theirs.
 */
export default function Demo() {
  useDocumentTitle('Sample analysis')
  const target = DEMO_ANALYSIS.target

  return (
    <div className="min-h-screen">
      <SiteHeader />

      <main id="main" className="mx-auto max-w-5xl px-5 py-12 sm:px-8 sm:py-16">
        <div className="panel flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:gap-6 sm:p-6">
          <FlaskConical size={20} className="shrink-0 text-brand-600" aria-hidden="true" />
          <p className="text-sm leading-relaxed text-ink-muted">
            <span className="font-medium text-ink">This is sample data.</span> It is one saved
            analysis of an invented resume against an invented posting, kept so the whole report
            can be read without an account. Nothing on this page was written by a model, and no
            real resume is involved.
          </p>
          <Link to="/signup" className="btn btn-primary shrink-0 sm:ml-auto">
            Run it on mine
            <ArrowRight size={16} aria-hidden="true" />
          </Link>
        </div>

        <header className="mb-8 mt-12">
          <p className="eyebrow">
            {target.jobTitle} · {target.company}
          </p>
          <h1 className="mt-3 text-display-md">Sample analysis</h1>
          <p className="mt-3 max-w-2xl text-sm text-ink-muted">
            A backend-leaning developer, two years in, against a posting that also wants
            containers and a cloud provider. {DEMO_ANALYSIS.target.resumeLabel} · every section
            below is what a real run produces.
          </p>
        </header>

        <AnalysisReport result={DEMO_ANALYSIS} />

        <section aria-labelledby="demo-cta" className="panel mt-10 p-6 text-center sm:p-10">
          <h2 id="demo-cta" className="text-display-md">
            Your turn.
          </h2>
          <p className="mx-auto mt-4 max-w-lg text-sm leading-relaxed text-ink-muted">
            Same report, your resume, the posting you are actually applying to. The scores will be
            different and the reasons will be yours.
          </p>

          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <Link to="/signup" className="btn btn-primary">
              Create an account
              <ArrowRight size={16} aria-hidden="true" />
            </Link>
            <Link to="/" className="btn btn-secondary">
              Back to the overview
            </Link>
          </div>
        </section>
      </main>

      <SiteFooter />
    </div>
  )
}
