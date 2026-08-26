import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import ChartFrame from './ChartFrame.jsx'
import ChartTooltip from './ChartTooltip.jsx'
import { AXIS, token } from '../../lib/chartTokens.js'

/**
 * How often the same requirement has come back missing.
 *
 * This is the only number in the product that spans analyses, and it is the most
 * actionable one in it: a skill absent from one comparison might be that posting's
 * quirk, while a skill absent from five is a gap in the resume. Frequency turns a pile
 * of individual verdicts into a decision about what to learn next.
 *
 * The x-axis is a count of analyses, so ticks are whole numbers — `allowDecimals` off,
 * because "missing in 2.5 analyses" is not a thing that can happen.
 *
 * The dashboard shows this same data as a compact strip, without an axis. That is the
 * glance; this is the reading. Two treatments of one dataset is right when the questions
 * differ — "is anything repeating" needs no axis, "what exactly, and how much more than
 * the next one" does.
 *
 * @param {object} props
 * @param {Array<{skill: string, occurrences: number}>} props.gaps
 * @param {number} [props.limit] rows to draw, longest first
 */
export default function GapFrequencyChart({ gaps, limit = 8 }) {
  const rows = gaps.slice(0, limit)
  if (rows.length === 0) return null

  return (
    <ChartFrame
      title="Gaps that keep coming back"
      lead="Counted across every analysis on your account. The top row is the one worth learning next."
      height={rows.length * 38 + 40}
      columns={['Skill', 'Analyses missing it']}
      rows={rows.map((gap) => [gap.skill, gap.occurrences])}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart
          data={rows}
          layout="vertical"
          margin={{ top: 0, right: 16, bottom: 0, left: 8 }}
          barSize={14}
        >
          <CartesianGrid stroke={AXIS.stroke} strokeDasharray="2 4" horizontal={false} />
          <XAxis type="number" allowDecimals={false} tick={AXIS.tick} stroke={AXIS.stroke} />
          <YAxis
            type="category"
            dataKey="skill"
            width={112}
            tick={AXIS.tick}
            stroke={AXIS.stroke}
          />
          <Tooltip content={<ChartTooltip />} cursor={{ fill: token('surface-sunken', 0.6) }} />
          <Bar
            dataKey="occurrences"
            name="Analyses missing it"
            fill={token('accent-500')}
            radius={[0, 3, 3, 0]}
          />
        </BarChart>
      </ResponsiveContainer>
    </ChartFrame>
  )
}
