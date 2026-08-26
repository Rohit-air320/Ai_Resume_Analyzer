/**
 * The tooltip every chart shares.
 *
 * Recharts' default tooltip is a white box with a grey border and a serif-ish default
 * font — fine, and visibly not part of this product. This one uses the same card
 * treatment as everything else and the mono face for numbers, so hovering a chart does
 * not open a window onto a different design.
 *
 * Recharts passes `active`, `payload` and `label`. When the pointer is not over a point
 * there is nothing to say, so the component renders nothing rather than an empty box.
 *
 * @param {object} props
 * @param {boolean} [props.active]
 * @param {Array<{name: string, value: number, color: string}>} [props.payload]
 * @param {string} [props.label]       heading for the hovered point
 * @param {Function} [props.formatLabel] turns the raw label into something readable
 */
export default function ChartTooltip({ active, payload, label, formatLabel }) {
  if (!active || !payload || payload.length === 0) return null

  return (
    <div className="card px-3 py-2 shadow-raised">
      {label ? (
        <p className="text-xs font-medium text-ink">{formatLabel ? formatLabel(label) : label}</p>
      ) : null}

      <ul className="mt-1 space-y-0.5">
        {payload.map((entry) => (
          <li key={entry.name} className="flex items-center gap-2 text-xs text-ink-muted">
            <span
              aria-hidden="true"
              className="h-1.5 w-1.5 shrink-0 rounded-full"
              style={{ backgroundColor: entry.color }}
            />
            {entry.name}
            <span data-numeric="" className="ml-auto pl-3 font-semibold text-ink">
              {entry.value}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}
