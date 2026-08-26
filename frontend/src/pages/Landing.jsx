import { Link } from 'react-router-dom'
import {
  ArrowRight,
  Gauge,
  KeyRound,
  Lightbulb,
  ListChecks,
  Lock,
  ScanLine,
  Target,
  X,
} from 'lucide-react'
import SiteHeader from '../components/marketing/SiteHeader.jsx'
import SiteFooter from '../components/marketing/SiteFooter.jsx'
import MatchRail from '../components/analysis/MatchRail.jsx'
import ScorePill from '../components/score/ScorePill.jsx'
import { DEMO_ANALYSIS } from '../features/demo/demoAnalysis.js'
import { SCORE_BANDS } from '../lib/scoreBands.js'

/**
 * The landing page.
 *
 * **The hero is the product, not a picture of it.** The panel on the right is the real
 * `MatchRail` — the same component the results page uses for its signature element — fed by
 * the demo fixture. A visitor's first look at ResumeIQ is therefore a look at ResumeIQ, and it
 * cannot drift from what they will see after signing up, because there is only one rail in the
 * codebase and one fixture behind every public surface.
 *
 * **The claims here are the ones the code can keep.** Nothing on this page promises a feature
 * that does not exist, and the section that would be missing from a template — "What it will
 * not do" — is the honest half of the pitch. The engine computes every number before a model
 * is contacted and `AdviceSanitiser` discards anything invented, so "it cannot add a skill you
 * do not have" is a description of the architecture rather than a marketing line.
 *
 * **The bands come from `SCORE_BANDS`.** Publishing the thresholds on the marketing page means
 * a visitor can check the number they eventually get, and reading them from the same module the
 * app scores with means the page cannot advertise a scale the product does not use.
 *
 * The numbered "how it works" list is numbered because it is genuinely a sequence — the score
 * cannot exist before the extraction, and the prose cannot exist before the score. Nothing else
 * on this page is numbered.
 */

const CAPABILITIES = [
  {
    icon: Gauge,
    title: 'An ATS score with its arithmetic',
    detail:
      'Headings, structure, parseability. Every line of the calculation is listed on the report, so the number is something you can check rather than something you have to trust.',
  },
  {
    icon: Target,
    title: 'A match score against this posting',
    detail:
      'Not a general resume grade. The same resume scores differently against two postings, because the two postings are asking for different things.',
  },
  {
    icon: ListChecks,
    title: 'Requirement-by-requirement coverage',
    detail:
      'Every skill the posting names, marked strong, partial or absent, weighted by how the posting itself phrased it. Unmet requirements are listed first.',
  },
  {
    icon: KeyRound,
    title: 'Keywords, and where they belong',
    detail:
      'Each suggested term comes with the bullet it honestly fits. A term with nowhere truthful to go is reported as absent and never suggested.',
  },
  {
    icon: ScanLine,
    title: 'A reading of all eight sections',
    detail:
      'Contact, summary, skills, experience, projects, education, certifications and formatting — each scored, each with a note saying what would raise it.',
  },
  {
    icon: Lightbulb,
    title: 'Projects and topics for this role',
    detail:
      'What to build and what to learn, chosen from the gaps in this comparison rather than from a generic list of trending technologies.',
  },
]

const STEPS = [
  {
    title: 'Upload your resume',
    detail:
      'PDF or DOCX. The text is extracted on the server, stored against your account, and used for nothing else.',
  },
  {
    title: 'Paste the job description',
    detail:
      'The posting is parsed into requirements and weighted by its own wording. "Must have" and "nice to have" are not the same requirement, and the score treats them differently.',
  },
  {
    title: 'The engine scores the pair',
    detail:
      'Arithmetic over two documents: skills, keywords, sections, experience relevance. No model is involved in any number, which is why the same pair always scores the same.',
  },
  {
    title: 'A writer explains the findings',
    detail:
      'The findings go to a language model whose only job is prose. It cannot add a skill, a gap or a keyword — the findings are fixed before it is asked, and anything it invents is discarded. If the provider is down, the report is written from the same findings without it.',
  },
]

const REFUSALS = [
  'Invent experience, skills or certifications you do not have.',
  'Suggest keyword stuffing. A term with no truthful placement is dropped, not padded in.',
  'Change your facts. Dates, titles and employers are yours and stay as written.',
  'Grade you against a generic "good resume". There is always a specific posting on the other side.',
]

export default function Landing() {
  const demoGaps = DEMO_ANALYSIS.missingSkills.length
  const heroMetrics = [
    { label: 'ATS score', value: DEMO_ANALYSIS.atsScore },
    { label: 'Job match', value: DEMO_ANALYSIS.jobMatchScore },
    { label: 'Gaps found', value: demoGaps },
  ]

  return (
    <div className="min-h-screen">
      <SiteHeader />

      <main>
        {/* Hero: the claim on the left, the product itself on the right. */}
        <section className="mx-auto max-w-6xl px-5 py-16 sm:px-8 sm:py-24">
          <div className="grid items-center gap-14 lg:grid-cols-[minmax(0,1fr)_minmax(0,28rem)] lg:gap-16">
            <div className="animate-fade-up">
              <p className="eyebrow">One resume · one posting · one score you can defend</p>

              <h1 className="mt-5 max-w-xl text-display-lg sm:text-display-xl">
                Read your resume the way the posting reads it.
              </h1>

              <p className="mt-6 max-w-xl text-base leading-relaxed text-ink-muted">
                Upload a PDF or DOCX, paste the job description, and get an ATS score, a match
                score, the requirements you have not evidenced, and the specific edits worth
                making. Every number arrives with the reason it landed there.
              </p>

              <div className="mt-9 flex flex-wrap gap-3">
                <Link to="/signup" className="btn btn-primary">
                  Analyse my resume
                  <ArrowRight size={16} aria-hidden="true" />
                </Link>
                <Link to="/demo" className="btn btn-secondary">
                  Read a sample analysis
                </Link>
              </div>

              <p className="mt-6 font-mono text-xs text-ink-subtle">
                No card. Your resume stays in your account and goes when you delete it.
              </p>
            </div>

            {/* The signature element, live, on the fixture every public page shares. */}
            <div className="panel p-6 sm:p-8">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="eyebrow">
                  Sample · {DEMO_ANALYSIS.target.jobTitle} at {DEMO_ANALYSIS.target.company}
                </p>
                <ScorePill score={DEMO_ANALYSIS.overallScore} />
              </div>

              <div className="mt-8">
                <MatchRail
                  detected={DEMO_ANALYSIS.detectedSkills}
                  missing={DEMO_ANALYSIS.missingSkills}
                />
              </div>

              <dl className="mt-9 flex gap-8 border-t border-line pt-5">
                {heroMetrics.map((metric) => (
                  <div key={metric.label}>
                    <dt className="eyebrow">{metric.label}</dt>
                    <dd data-numeric="" className="mt-1.5 text-2xl text-ink">
                      {metric.value}
                    </dd>
                  </div>
                ))}
              </dl>

              <p className="mt-6 text-xs leading-relaxed text-ink-subtle">
                Two requirements in this posting have no evidence behind them. That is the
                report — not a rejection, and not a suggestion to claim them.
              </p>
            </div>
          </div>
        </section>

        {/* What the report contains. */}
        <section aria-labelledby="what-you-get" className="border-t border-line bg-surface-sunken">
          <div className="mx-auto max-w-6xl px-5 py-16 sm:px-8 sm:py-20">
            <p className="eyebrow">What comes back</p>
            <h2 id="what-you-get" className="mt-4 max-w-2xl text-display-md">
              Twelve findings, and the reasoning behind each one.
            </h2>

            <ul className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {CAPABILITIES.map((item) => {
                const Icon = item.icon

                return (
                  <li key={item.title} className="card p-5 sm:p-6">
                    <Icon size={18} className="text-brand-600" aria-hidden="true" />
                    <h3 className="mt-4 text-sm font-semibold text-ink">{item.title}</h3>
                    <p className="mt-2 text-sm leading-relaxed text-ink-muted">{item.detail}</p>
                  </li>
                )
              })}
            </ul>
          </div>
        </section>

        {/* A real sequence, so it gets real numbers. */}
        <section aria-labelledby="how-it-works" className="border-t border-line">
          <div className="mx-auto max-w-6xl px-5 py-16 sm:px-8 sm:py-20">
            <p className="eyebrow">How it works</p>
            <h2 id="how-it-works" className="mt-4 max-w-2xl text-display-md">
              The numbers are computed. Only the writing is generated.
            </h2>
            <p className="mt-5 max-w-2xl text-sm leading-relaxed text-ink-muted">
              That order matters more than anything else on this page. A tool that asks a model
              for a score gives a different answer to the same question twice and cannot say why
              it gave either.
            </p>

            <ol className="mt-12 grid gap-8 md:grid-cols-2 lg:grid-cols-4">
              {STEPS.map((step, index) => (
                <li key={step.title} className="border-t border-line-strong pt-5">
                  <span data-numeric="" className="font-mono text-xs text-brand-600">
                    {String(index + 1).padStart(2, '0')}
                  </span>
                  <h3 className="mt-3 text-sm font-semibold text-ink">{step.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-ink-muted">{step.detail}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        {/* The scale, published. */}
        <section aria-labelledby="score-bands" className="border-t border-line bg-surface-sunken">
          <div className="mx-auto max-w-6xl px-5 py-16 sm:px-8 sm:py-20">
            <div className="grid gap-12 lg:grid-cols-[minmax(0,22rem)_minmax(0,1fr)] lg:gap-16">
              <div>
                <p className="eyebrow">The scale</p>
                <h2 id="score-bands" className="mt-4 text-display-md">
                  Five bands, published up front.
                </h2>
                <p className="mt-5 text-sm leading-relaxed text-ink-muted">
                  Every score in the product falls into one of these, and the band travels with
                  the number wherever it appears. Knowing the thresholds before you upload
                  anything is the difference between a score and a verdict you cannot interrogate.
                </p>
              </div>

              <ul className="divide-y divide-line">
                {SCORE_BANDS.map((band) => (
                  <li key={band.id} className="flex items-center gap-5 py-4 first:pt-0 last:pb-0">
                    <span data-numeric="" className="w-20 shrink-0 font-mono text-xs text-ink-muted">
                      {band.min}–{band.max}
                    </span>
                    <span className={`text-sm font-medium ${band.text}`}>{band.label}</span>
                    <span
                      aria-hidden="true"
                      className={`ml-auto h-1 w-16 rounded-full sm:w-24 ${band.bg}`}
                    />
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </section>

        {/* The honest half of the pitch. */}
        <section aria-labelledby="refusals" className="border-t border-line">
          <div className="mx-auto max-w-6xl px-5 py-16 sm:px-8 sm:py-20">
            <div className="grid gap-12 lg:grid-cols-2 lg:gap-16">
              <div>
                <p className="eyebrow">What it will not do</p>
                <h2 id="refusals" className="mt-4 text-display-md">
                  A resume that wins an interview has to survive it.
                </h2>
                <p className="mt-5 text-sm leading-relaxed text-ink-muted">
                  Padding a resume with words is easy and it fails in the room. Every suggestion
                  here is tied to something already in your document, which is a structural
                  property rather than a promise: the findings are computed before any model is
                  asked, and a suggestion that does not match one is discarded on the way back.
                </p>
              </div>

              <ul className="space-y-4">
                {REFUSALS.map((refusal) => (
                  <li key={refusal} className="flex gap-3">
                    <X size={16} className="mt-0.5 shrink-0 text-danger-600" aria-hidden="true" />
                    <span className="text-sm leading-relaxed text-ink">{refusal}</span>
                  </li>
                ))}

                <li className="card flex gap-3 p-5">
                  <Lock size={16} className="mt-0.5 shrink-0 text-ink-subtle" aria-hidden="true" />
                  <span className="text-sm leading-relaxed text-ink-muted">
                    Your resume is never public, never appears in a URL, and is not written to
                    any log. The AI provider key lives on the server, so the browser never holds
                    it and never talks to a provider directly.
                  </span>
                </li>
              </ul>
            </div>
          </div>
        </section>

        {/* Close. */}
        <section aria-labelledby="get-started" className="border-t border-line bg-surface-sunken">
          <div className="mx-auto max-w-6xl px-5 py-16 text-center sm:px-8 sm:py-20">
            <h2 id="get-started" className="mx-auto max-w-xl text-display-md">
              See what the posting is asking for.
            </h2>
            <p className="mx-auto mt-5 max-w-lg text-sm leading-relaxed text-ink-muted">
              One resume and one job description is the whole input. The first analysis takes
              about a minute, most of it spent finding the posting.
            </p>

            <div className="mt-9 flex flex-wrap justify-center gap-3">
              <Link to="/signup" className="btn btn-primary">
                Create an account
                <ArrowRight size={16} aria-hidden="true" />
              </Link>
              <Link to="/demo" className="btn btn-secondary">
                Read the sample first
              </Link>
            </div>

            <p className="mt-6 font-mono text-xs text-ink-subtle">
              The sample needs no account at all.
            </p>
          </div>
        </section>
      </main>

      <SiteFooter />
    </div>
  )
}
