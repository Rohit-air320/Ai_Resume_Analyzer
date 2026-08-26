import { useEffect, useState } from 'react'
import { Loader2 } from 'lucide-react'

/**
 * What the user looks at while an analysis runs.
 *
 * **There is no percentage bar here, on purpose.** The request is one synchronous POST;
 * the browser has no idea how far through it the server is. A bar that fills on a timer
 * is a lie that gets found out the moment a slow model run leaves it sitting at 97%.
 * What this shows instead is true: the stages the server actually performs, in order,
 * and how long we have been waiting.
 *
 * The elapsed counter exists because the honest answer to "is this stuck?" is a number
 * that keeps moving. After a while the copy admits the model is the slow part, which is
 * more reassuring than a spinner that says nothing.
 */

const STAGES = [
  'Reading your resume text',
  'Pulling requirements out of the posting',
  'Matching skills and keywords',
  'Writing suggestions',
]

export default function ProcessingPanel({ resumeLabel, jobTitle }) {
  const [seconds, setSeconds] = useState(0)

  useEffect(() => {
    const timer = setInterval(() => setSeconds((value) => value + 1), 1000)
    return () => clearInterval(timer)
  }, [])

  return (
    <section className="panel px-6 py-10 text-center sm:px-10" aria-busy="true">
      <div className="mx-auto flex h-11 w-11 items-center justify-center rounded-full bg-brand-600/10">
        <Loader2 size={20} className="animate-spin text-brand-600" aria-hidden="true" />
      </div>

      <h2 className="mt-5 text-lg font-medium text-ink">Analysing your resume</h2>
      <p className="mx-auto mt-2 max-w-md text-sm text-ink-muted">
        {resumeLabel && jobTitle ? (
          <>
            Scoring <span className="text-ink">{resumeLabel}</span> against{' '}
            <span className="text-ink">{jobTitle}</span>.
          </>
        ) : (
          'This usually takes a few seconds.'
        )}
      </p>

      {/* Stages, not steps: the list says what the work is, and none of them claims to be done. */}
      <ul className="mx-auto mt-7 max-w-xs space-y-2.5 text-left">
        {STAGES.map((stage) => (
          <li key={stage} className="flex items-center gap-3 text-sm text-ink-muted">
            <span aria-hidden="true" className="h-1.5 w-1.5 shrink-0 rounded-full bg-brand-500/70" />
            {stage}
          </li>
        ))}
      </ul>

      <p className="mt-7 text-xs text-ink-subtle" aria-live="polite">
        <span data-numeric="">{seconds}</span>s elapsed
        {seconds > 20 ? ' — the language model is the slow part. Keep this tab open.' : ''}
      </p>
    </section>
  )
}
