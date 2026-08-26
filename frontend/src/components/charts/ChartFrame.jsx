/**
 * The frame every chart in this product sits in.
 *
 * A chart is a picture of numbers, and a picture is unreadable to a screen reader, to a
 * text-only browser, and to anyone whose SVG failed to paint. So this component draws the
 * chart *and* the same data as a table, hides whichever one is not useful to you, and
 * makes it impossible to add a chart without the alternative — the table is a required
 * prop, not an option somebody remembers on a good day.
 *
 * The chart itself is `aria-hidden`. Left visible, a screen reader walks an SVG full of
 * `<path>` elements and reads nothing of value; hidden, the table beneath it is the whole
 * content. `sr-only` rather than `display: none` because a hidden table is not announced
 * at all, and this one is the accessible version of the graphic.
 *
 * Height is a number of pixels, not a ratio. Recharts' ResponsiveContainer measures its
 * parent, and a parent sized by its content is a parent of height zero — the chart then
 * renders nothing at all, silently. Fixing the height in one place stops that being
 * rediscovered per chart.
 *
 * @param {object} props
 * @param {string} props.title       the heading, which also labels the table
 * @param {string} [props.lead]      one sentence on how to read it
 * @param {boolean} [props.captionHidden] hide the caption visually, keeping it as the
 *   figure's accessible name. For a chart inside a panel that already carries the same
 *   heading — two identical headings stacked is a worse outcome than a hidden one, and
 *   deleting the caption instead would take the table's caption with it.
 * @param {number} [props.height]    chart height in pixels
 * @param {string[]} props.columns   table column headings
 * @param {Array<Array<string|number>>} props.rows  table body, one array per row
 * @param {import('react').ReactNode} [props.legend] a visual key, also hidden from
 *   assistive technology — it names the same numbers the table already carries
 * @param {import('react').ReactNode} props.children the Recharts tree
 */
export default function ChartFrame({
  title,
  lead,
  captionHidden = false,
  height = 240,
  columns,
  rows,
  legend,
  children,
}) {
  const id = `chart-${title.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`

  return (
    <figure aria-labelledby={id}>
      <figcaption className={captionHidden ? 'sr-only' : undefined}>
        <h3 id={id} className="text-sm font-semibold text-ink">
          {title}
        </h3>
        {lead ? <p className="mt-1 text-xs text-ink-muted">{lead}</p> : null}
      </figcaption>

      <div
        aria-hidden="true"
        className={captionHidden ? 'w-full' : 'mt-4 w-full'}
        style={{ height: `${height}px` }}
      >
        {children}
      </div>

      {legend ? <div aria-hidden="true">{legend}</div> : null}

      <table className="sr-only">
        <caption>{title}</caption>
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column} scope="col">
                {column}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={String(row[0])}>
              <th scope="row">{row[0]}</th>
              {row.slice(1).map((cell, index) => (
                <td key={columns[index + 1]}>{cell}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  )
}
