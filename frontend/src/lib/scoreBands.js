/**
 * Score bands — the single source of truth for what a number means.
 *
 * The thresholds come straight from the product spec (0-39, 40-59, 60-74, 75-89,
 * 90-100). Every class name is written out as a literal so Tailwind's scanner sees
 * it; building class strings dynamically would get them purged from the bundle.
 */

export const SCORE_BANDS = [
  {
    id: 'critical',
    min: 0,
    max: 39,
    label: 'Needs major improvement',
    text: 'text-band-critical',
    bg: 'bg-band-critical',
    softBg: 'bg-band-critical/10',
    border: 'border-band-critical/30',
    stroke: 'stroke-band-critical',
  },
  {
    id: 'low',
    min: 40,
    max: 59,
    label: 'Needs improvement',
    text: 'text-band-low',
    bg: 'bg-band-low',
    softBg: 'bg-band-low/10',
    border: 'border-band-low/30',
    stroke: 'stroke-band-low',
  },
  {
    id: 'moderate',
    min: 60,
    max: 74,
    label: 'Moderate match',
    text: 'text-band-moderate',
    bg: 'bg-band-moderate',
    softBg: 'bg-band-moderate/10',
    border: 'border-band-moderate/30',
    stroke: 'stroke-band-moderate',
  },
  {
    id: 'strong',
    min: 75,
    max: 89,
    label: 'Strong match',
    text: 'text-band-strong',
    bg: 'bg-band-strong',
    softBg: 'bg-band-strong/10',
    border: 'border-band-strong/30',
    stroke: 'stroke-band-strong',
  },
  {
    id: 'excellent',
    min: 90,
    max: 100,
    label: 'Excellent match',
    text: 'text-band-excellent',
    bg: 'bg-band-excellent',
    softBg: 'bg-band-excellent/10',
    border: 'border-band-excellent/30',
    stroke: 'stroke-band-excellent',
  },
]

export function clampScore(score) {
  const numeric = Number(score)
  if (!Number.isFinite(numeric)) return 0
  return Math.min(100, Math.max(0, Math.round(numeric)))
}

/** Returns the band a score falls into. Out-of-range and junk values clamp to 0-100. */
export function bandForScore(score) {
  const value = clampScore(score)
  return SCORE_BANDS.find((band) => value >= band.min && value <= band.max) ?? SCORE_BANDS[0]
}

export function labelForScore(score) {
  return bandForScore(score).label
}
