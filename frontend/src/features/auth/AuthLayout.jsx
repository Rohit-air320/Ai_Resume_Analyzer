import { Link } from 'react-router-dom'

/**
 * Shared frame for the sign-in and sign-up screens.
 *
 * The left column is not decoration. It is a static instance of the product's
 * signature element — the match rail, which lays a posting's requirements against
 * the evidence a resume actually provides. Putting it here means the first thing a
 * visitor sees is what the tool does, in the tool's own vocabulary, rather than a
 * stock illustration or a gradient. It carries no live data and makes no request, so
 * it costs nothing on a screen where the only job is to get a password typed.
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
  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[minmax(0,1fr)_28rem]">
      <MatchRailPreview />

      <main className="flex min-h-screen flex-col justify-center border-line px-6 py-12 sm:px-10 lg:border-l">
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
 * Verdicts come from the score-band tokens, so this preview cannot drift from the
 * colours the real results screen uses. The example is the spec's demo profile: a
 * Java and Spring Boot developer against a posting that also wants containers.
 */
const RAIL_ROWS = [
  { requirement: 'Java 17', verdict: 'Strong', evidence: '3 projects', tone: 'excellent' },
  { requirement: 'Spring Boot', verdict: 'Strong', evidence: 'REST APIs', tone: 'excellent' },
  { requirement: 'React', verdict: 'Strong', evidence: 'Hooks, Router', tone: 'excellent' },
  { requirement: 'MySQL', verdict: 'Partial', evidence: 'Named once', tone: 'moderate' },
  { requirement: 'Docker', verdict: 'Missing', evidence: 'No evidence', tone: 'critical' },
  { requirement: 'AWS', verdict: 'Missing', evidence: 'No evidence', tone: 'critical' },
]

const TONES = {
  excellent: { text: 'text-band-excellent', rail: 'bg-band-excellent/50' },
  moderate: { text: 'text-band-moderate', rail: 'bg-band-moderate/50' },
  critical: { text: 'text-band-critical', rail: 'bg-band-critical/40' },
}

function MatchRailPreview() {
  return (
    <aside className="hidden bg-surface-sunken px-12 py-16 lg:flex lg:flex-col lg:justify-center">
      <div className="mx-auto w-full max-w-lg">
        <p className="eyebrow">Match rail · Senior Java Developer</p>
        <p className="mt-4 max-w-md font-display text-2xl font-semibold leading-tight">
          Every requirement in the posting, next to what your resume actually proves.
        </p>

        <ul className="mt-10 space-y-3.5">
          {RAIL_ROWS.map((row) => (
            <li key={row.requirement} className="grid grid-cols-[8rem_1fr_5.5rem] items-center gap-4">
              <span className="truncate font-mono text-xs text-ink">{row.requirement}</span>
              <span className={`h-px w-full ${TONES[row.tone].rail}`} aria-hidden="true" />
              <span className={`text-right font-mono text-xs ${TONES[row.tone].text}`}>
                {row.verdict}
              </span>
            </li>
          ))}
        </ul>

        <dl className="mt-12 flex gap-10 border-t border-line pt-6">
          <Metric label="ATS score" value="84" />
          <Metric label="Job match" value="81" />
          <Metric label="Gaps found" value="2" />
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
