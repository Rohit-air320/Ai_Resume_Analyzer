/**
 * Colours for Recharts, expressed as CSS custom properties rather than hex.
 *
 * Recharts needs a concrete value for `stroke` and `fill`, so a Tailwind class is no use
 * here — but `rgb(var(--brand-600))` is a legal SVG paint value, and the variable is
 * resolved by the browser against whatever `:root` or `.dark` currently says. That means
 * every chart recolours on the theme swap with no JavaScript, no re-render and no second
 * palette to keep in step. Reading the computed value into JS instead is the version of
 * this that breaks: it samples the theme once, at mount, and a chart drawn before the
 * user hits the theme toggle keeps its old colours until something else re-renders it.
 *
 * Alpha still works — `rgb(var(--line) / 0.6)` — because the tokens are stored as bare
 * `R G B` triplets for exactly this reason.
 */

export const token = (name, alpha) =>
  alpha === undefined ? `rgb(var(--${name}))` : `rgb(var(--${name}) / ${alpha})`

/** The three lines on the score trend, in the order they are drawn. */
export const SERIES = {
  overall: { key: 'overall', label: 'Overall', color: token('brand-600'), width: 2.5 },
  ats: { key: 'ats', label: 'ATS', color: token('accent-500'), width: 1.5 },
  jobMatch: { key: 'jobMatch', label: 'Job match', color: token('ink-subtle'), width: 1.5 },
}

export const AXIS = {
  stroke: token('line'),
  // Axis labels are numbers, so they get the mono face the rest of the product's
  // numerals use. Written out because an SVG tick is not a Tailwind-styled element.
  tick: { fill: token('ink-subtle'), fontSize: 11, fontFamily: '"JetBrains Mono", ui-monospace, monospace' },
}

/** Band colours, for a chart that colours a value by what the value means. */
export const BAND_PAINT = {
  critical: token('band-critical'),
  low: token('band-low'),
  moderate: token('band-moderate'),
  strong: token('band-strong'),
  excellent: token('band-excellent'),
}
