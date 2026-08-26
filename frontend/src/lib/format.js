/**
 * Turning API values into readable English.
 *
 * All of it uses the browser's own `Intl` and `toLocaleString`, so a date renders in
 * the reader's locale rather than in the developer's. Hardcoding a format is the bug
 * where a European user reads 03/04 as the fourth of March.
 *
 * Every function tolerates junk. These render inside lists that come from the network,
 * and a missing timestamp must produce a dash, not a page that fails to paint.
 */

const DATE_ONLY = { day: 'numeric', month: 'short', year: 'numeric' }
const DATE_AND_TIME = { ...DATE_ONLY, hour: 'numeric', minute: '2-digit' }

function parse(value) {
  if (!value) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatDate(value) {
  const date = parse(value)
  return date ? date.toLocaleDateString(undefined, DATE_ONLY) : '—'
}

export function formatDateTime(value) {
  const date = parse(value)
  return date ? date.toLocaleString(undefined, DATE_AND_TIME) : '—'
}

/**
 * "3 days ago", falling back to a date past a fortnight.
 *
 * Relative time is easier to read for anything recent and worse for anything old —
 * "97 days ago" makes a reader do arithmetic they did not ask for.
 */
export function formatRelative(value) {
  const date = parse(value)
  if (!date) return '—'

  const seconds = Math.round((Date.now() - date.getTime()) / 1000)
  if (seconds < 45) return 'just now'
  if (seconds < 90) return 'a minute ago'

  const minutes = Math.round(seconds / 60)
  if (minutes < 60) return `${minutes} minutes ago`

  const hours = Math.round(minutes / 60)
  if (hours < 24) return hours === 1 ? 'an hour ago' : `${hours} hours ago`

  const days = Math.round(hours / 24)
  if (days === 1) return 'yesterday'
  if (days <= 14) return `${days} days ago`

  return formatDate(value)
}

export function formatBytes(bytes) {
  const numeric = Number(bytes)
  if (!Number.isFinite(numeric) || numeric <= 0) return '—'
  if (numeric < 1024) return `${numeric} B`
  if (numeric < 1024 * 1024) return `${Math.round(numeric / 1024)} KB`
  return `${(numeric / (1024 * 1024)).toFixed(1)} MB`
}

/** `count(2, 'resume')` → "2 resumes". Only for words that pluralise with an s. */
export function count(value, singular, plural = `${singular}s`) {
  const numeric = Number(value) || 0
  return `${numeric} ${numeric === 1 ? singular : plural}`
}

/**
 * Turns an enum from the API into a label, without a lookup table per enum.
 *
 * `NICE_TO_HAVE` → "Nice to have". The backend's enums are already written as words,
 * so a map from every constant to a caption would be a second place to update every
 * time one is added — and the failure mode of forgetting is a screen that shows a
 * database constant to a user.
 */
export function humanise(value) {
  if (!value) return ''
  const words = String(value).toLowerCase().replace(/_/g, ' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}
