import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts'
import ChartFrame from './ChartFrame.jsx'
import ChartTooltip from './ChartTooltip.jsx'
import { token } from '../../lib/chartTokens.js'

/**
 * Where the overall score came from, as a ring.
 *
 * The ring is the hundred points available. Each coloured segment is the points one
 * component actually earned, and the grey segment at the end is what was lost — so the
 * chart answers "which part cost me the most" by making the missing arc as visible as the
 * earned ones. A donut of the earned points alone would look identical for a score of 95
 * and a score of 45, which is the version of this chart that means nothing.
 *
 * Rows with `outOf === 0` are dropped: the API uses that to mark a note that carries
 * context rather than points, and a zero-width slice is not a slice.
 *
 * Colours walk the brand ramp rather than the score bands. A band colour here would say
 * "this component is bad", when what the segment measures is how much of the total it
 * accounts for — red for the largest earned segment would be an outright lie.
 *
 * @param {object} props
 * @param {Array<{label: string, earned: number, outOf: number}>} props.breakdown
 */

const RAMP = [
  token('brand-600'),
  token('accent-500'),
  token('brand-400'),
  token('accent-400'),
  token('brand-800'),
  token('accent-600'),
]

const LOST = token('surface-sunken')

export default function ScoreBreakdownDonut({ breakdown }) {
  const scored = breakdown.filter((reason) => reason.outOf > 0)
  if (scored.length === 0) return null

  const available = scored.reduce((total, reason) => total + reason.outOf, 0)
  const earned = scored.reduce((total, reason) => total + reason.earned, 0)
  const lost = Math.max(0, available - earned)

  const slices = scored.map((reason, index) => ({
    name: reason.label,
    value: reason.earned,
    fill: RAMP[index % RAMP.length],
  }))

  const data = lost > 0 ? [...slices, { name: 'Not earned', value: lost, fill: LOST }] : slices

  return (
    <ChartFrame
      title="Points earned"
      lead={`${earned} of ${available} available, by component.`}
      height={220}
      columns={['Component', 'Earned', 'Available']}
      rows={[
        ...scored.map((reason) => [reason.label, reason.earned, reason.outOf]),
        ['Not earned', lost, available],
      ]}
      legend={
        // HTML rather than Recharts' legend, so it uses the product's own type.
        <ul className="mt-4 grid gap-x-5 gap-y-1.5 sm:grid-cols-2">
          {data.map((slice) => (
            <li key={slice.name} className="flex items-center gap-2 text-xs text-ink-muted">
              <span
                className="h-2 w-2 shrink-0 rounded-[2px]"
                style={{ backgroundColor: slice.fill }}
              />
              <span className="truncate">{slice.name}</span>
              <span data-numeric="" className="ml-auto text-ink">
                {slice.value}
              </span>
            </li>
          ))}
        </ul>
      }
    >
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie
            data={data}
            dataKey="value"
            nameKey="name"
            innerRadius="62%"
            outerRadius="92%"
            paddingAngle={1.5}
            strokeWidth={0}
            startAngle={90}
            endAngle={-270}
          >
            {data.map((slice) => (
              <Cell key={slice.name} fill={slice.fill} />
            ))}
          </Pie>
          <Tooltip content={<ChartTooltip />} />
        </PieChart>
      </ResponsiveContainer>
    </ChartFrame>
  )
}
