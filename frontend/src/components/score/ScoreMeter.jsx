import { bandForScore, clampScore } from '../../lib/scoreBands.js'

/**
 * One score as a labelled bar.
 *
 * Used for the six component scores, where the interesting information is not any
 * single number but which of them is dragging the others down — a shared baseline and
 * a shared scale make that readable at a glance in a way six separate dials do not.
 *
 * It is a `meter` rather than a `progressbar`: progress implies something is happening,
 * and this is a measurement that has already been taken. `aria-valuetext` carries the
 * band name so a screen reader hears "72, moderate match" rather than a bare number,
 * which is the same pairing the sighted reader gets from the colour.
 *
 * An absent score renders as a dash and an empty track. That happens on a failed
 * analysis, and defaulting it to 0 would show a verdict the engine never reached.
 */
export default function ScoreMeter({ label, score, note }) {
  const missing = score === null || score === undefined
  const value = clampScore(score)
  const band = bandForScore(score)

  return (
    <div>
      <div className="flex items-baseline justify-between gap-3">
        <span className="text-sm font-medium text-ink">{label}</span>
        <span data-numeric="" className={`text-sm font-semibold ${missing ? 'text-ink-subtle' : band.text}`}>
          {missing ? '—' : value}
        </span>
      </div>

      <div
        role="meter"
        aria-valuenow={missing ? undefined : value}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuetext={missing ? 'Not scored' : `${value} out of 100, ${band.label}`}
        aria-label={label}
        className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-surface-sunken"
      >
        <span
          className={`block h-full rounded-full transition-[width] duration-700 ease-out-expo ${band.bg}`}
          style={{ width: missing ? '0%' : `${value}%` }}
        />
      </div>

      {note ? <p className="mt-2 text-xs text-ink-muted">{note}</p> : null}
    </div>
  )
}
