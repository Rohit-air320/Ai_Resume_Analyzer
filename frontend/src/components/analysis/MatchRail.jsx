import { humanise } from '../../lib/format.js'

/**
 * The match rail — the product's signature element, on real data.
 *
 * One row per requirement the posting names: the requirement, a hairline reaching across
 * to the verdict, and the verdict. The line is the whole idea. A requirement your resume
 * proves gets a solid connector in the band colour; one it only hints at gets a solid line
 * in the warning band; one it does not mention gets a *broken* line, because the
 * connection genuinely was not made. The metaphor is the data, not decoration on top of it.
 *
 * Rows are grouped by how much the posting cared, then ordered missing-first inside each
 * group. That puts the two rows a reader has to act on at the very top of the rail, and it
 * means the shape of the whole block — where the dashes are — reads as "how bad is this"
 * before a single word is parsed.
 *
 * No notes here on purpose. The rail is for scanning thirty requirements in three seconds;
 * the evidence behind each one is a paragraph, and paragraphs belong in the skill sections
 * further down the page. A rail with a sentence per row is just the same list again.
 *
 * The connector is `aria-hidden`, so a screen reader hears "Spring Boot, Strong" — the
 * information, without the drawing.
 *
 * @param {object} props
 * @param {Array<{name: string, status: string, importance: string}>} [props.detected]
 * @param {Array<{name: string, status: string, importance: string}>} [props.missing]
 */

const LEVELS = ['CRITICAL', 'IMPORTANT', 'NICE_TO_HAVE']

const SEVERITY = { MISSING: 0, PARTIAL: 1, STRONG: 2 }

const TONES = {
  STRONG: { text: 'text-band-excellent', rail: 'border-band-excellent/60' },
  PARTIAL: { text: 'text-band-moderate', rail: 'border-band-moderate/70' },
  MISSING: { text: 'text-band-critical', rail: 'border-dashed border-band-critical/70' },
}

const FALLBACK = { text: 'text-ink-subtle', rail: 'border-line' }

export default function MatchRail({ detected = [], missing = [] }) {
  const rows = [
    ...detected.map((skill) => ({ ...skill, status: skill.status ?? 'PARTIAL' })),
    ...missing.map((skill) => ({ ...skill, status: 'MISSING' })),
  ]

  if (rows.length === 0) return null

  // An importance the posting never used is not drawn, and anything the API sends that is
  // not one of the three lands in a final group rather than vanishing from the rail.
  const known = new Set(LEVELS)
  const groups = [...LEVELS, null]
    .map((level) => ({
      level,
      label: level ? humanise(level) : 'Also mentioned',
      rows: rows
        .filter((row) => (level ? row.importance === level : !known.has(row.importance)))
        .sort((a, b) => (SEVERITY[a.status] ?? 9) - (SEVERITY[b.status] ?? 9)),
    }))
    .filter((group) => group.rows.length > 0)

  // Staggered by position in the whole rail, not within the group, so the reveal runs top
  // to bottom once instead of restarting at each heading. Capped so a posting with forty
  // requirements still finishes animating this decade. Computed here rather than during
  // render so nothing is being counted while JSX is being produced.
  let position = 0
  const staggered = groups.map((group) => ({
    ...group,
    rows: group.rows.map((row) => ({ ...row, delay: Math.min(position++, 14) * 40 })),
  }))

  return (
    <div className="space-y-7">
      {staggered.map((group) => (
        <div key={group.label}>
          <p className="eyebrow text-ink-subtle">{group.label}</p>

          <ul className="mt-3.5 space-y-2.5">
            {group.rows.map((row) => {
              const tone = TONES[row.status] ?? FALLBACK

              return (
                <li
                  key={`${row.name}-${row.status}`}
                  className="grid animate-fade-up grid-cols-[minmax(0,1fr)_auto] items-center gap-x-3 sm:grid-cols-[minmax(0,11rem)_1fr_4.5rem] sm:gap-x-4"
                  style={{ animationDelay: `${row.delay}ms` }}
                >
                  <span className="truncate font-mono text-xs text-ink">{row.name}</span>
                  <span aria-hidden="true" className={`hidden border-t sm:block ${tone.rail}`} />
                  <span className={`text-right font-mono text-xs ${tone.text}`}>
                    {humanise(row.status)}
                  </span>
                </li>
              )
            })}
          </ul>
        </div>
      ))}
    </div>
  )
}
