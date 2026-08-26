import { Check } from 'lucide-react'

/**
 * Where you are in the three steps of a new analysis.
 *
 * Numbers are used here because this genuinely is a sequence — an analysis cannot be run
 * before both documents exist — and numbering something that is not ordered is one of the
 * quickest ways to make an interface look decorative rather than informative.
 *
 * A completed step is a button back to itself, which is the difference between a progress
 * indicator and navigation: changing your mind about the resume after picking a posting
 * should not mean starting again. A step ahead of the current one is inert, because there
 * is nothing there yet to look at.
 *
 * The `ol` and `aria-current="step"` carry the same information to a screen reader that the
 * filled circle carries visually.
 */
export default function Stepper({ steps, current, onGoTo }) {
  return (
    <ol className="flex flex-wrap items-center gap-x-3 gap-y-2 pb-6">
      {steps.map((step, index) => {
        const position = index + 1
        const state = position < current ? 'done' : position === current ? 'current' : 'ahead'
        const canReturn = state === 'done' && typeof onGoTo === 'function'

        const body = (
          <>
            <span
              aria-hidden="true"
              className={[
                'flex h-6 w-6 items-center justify-center rounded-full font-mono text-xs',
                state === 'done' ? 'bg-brand-600/12 text-brand-700' : '',
                state === 'current' ? 'bg-brand-600 text-white' : '',
                state === 'ahead' ? 'border border-line-strong text-ink-subtle' : '',
              ].join(' ')}
            >
              {state === 'done' ? <Check size={13} /> : position}
            </span>
            <span
              className={`text-sm ${state === 'current' ? 'font-medium text-ink' : 'text-ink-muted'}`}
            >
              {step}
            </span>
          </>
        )

        return (
          <li key={step} className="flex items-center gap-3">
            {canReturn ? (
              <button
                type="button"
                onClick={() => onGoTo(position)}
                className="flex items-center gap-2 rounded-lg px-1.5 py-1 hover:bg-surface-sunken"
              >
                {body}
              </button>
            ) : (
              <span
                className="flex items-center gap-2 px-1.5 py-1"
                aria-current={state === 'current' ? 'step' : undefined}
              >
                {body}
              </span>
            )}

            {position < steps.length ? (
              <span aria-hidden="true" className="h-px w-6 bg-line-strong sm:w-10" />
            ) : null}
          </li>
        )
      })}
    </ol>
  )
}
