import { bandForScore, clampScore, labelForScore } from '../../lib/scoreBands.js'

/**
 * A score and what it means, together.
 *
 * The number and the band label are one component on purpose: "78" alone is a fact
 * nobody can act on, and the whole argument of this product is that a score has to
 * explain itself. The colour comes from the band rather than from a prop, so no screen
 * can decide for itself that 62 looks good.
 *
 * The label is not the only carrier of meaning by design — colour plus text means the
 * band survives both a screenshot in greyscale and a reader who cannot distinguish
 * amber from teal.
 */
export default function ScorePill({ score, size = 'md' }) {
  const band = bandForScore(score)
  const value = clampScore(score)
  const isLarge = size === 'lg'

  return (
    <span
      className={`inline-flex items-center gap-2 rounded-full border px-3 py-1 ${band.softBg} ${band.border}`}
    >
      <span
        data-numeric=""
        className={`font-semibold ${band.text} ${isLarge ? 'text-lg' : 'text-sm'}`}
      >
        {value}
      </span>
      <span className={`text-xs font-medium ${band.text}`}>{labelForScore(score)}</span>
    </span>
  )
}
