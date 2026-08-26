import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import ChartFrame from './ChartFrame.jsx'
import ChartTooltip from './ChartTooltip.jsx'
import { AXIS, SERIES } from '../../lib/chartTokens.js'
import { formatDate } from '../../lib/format.js'

/**
 * Score history: three lines over as many analyses as the account has.
 *
 * This is the one chart in the product where the shape genuinely is the information. A
 * single score answers "how did I do"; the line answers "is what I am doing working",
 * and that question has no textual equivalent short of reading thirty numbers in order.
 *
 * Overall is drawn last and heaviest because it is the number the product reports; ATS and
 * job match are the two components people act on, and they are thin because they are
 * context for the overall line rather than three equal claims.
 *
 * The y-axis is pinned to 0-100 rather than fitted to the data. Recharts would happily
 * scale 71-78 across the full height and turn seven points of drift into a cliff — which
 * is the most common way a chart lies without anybody deciding to lie.
 *
 * @param {object} props
 * @param {Array<{recordedAt: string, overall: number, ats: number, jobMatch: number}>} props.points
 * @param {boolean} [props.captionHidden] for callers whose panel already says "Score history"
 */
export default function ScoreTrendChart({ points, captionHidden = false }) {
  const rows = points.map((point) => [
    formatDate(point.recordedAt),
    point.overall ?? '—',
    point.ats ?? '—',
    point.jobMatch ?? '—',
  ])

  return (
    <ChartFrame
      title="Score history"
      lead="Oldest on the left. The axis is fixed to 0-100, so the slope is the real one."
      captionHidden={captionHidden}
      height={220}
      columns={['Analysed', 'Overall', 'ATS', 'Job match']}
      rows={rows}
      legend={
        // Three unlabelled lines is a puzzle. Swatches match the stroke weights above, so
        // the thin lines read as secondary here too.
        <ul className="mt-3 flex flex-wrap gap-x-5 gap-y-1.5">
          {[SERIES.overall, SERIES.ats, SERIES.jobMatch].map((series) => (
            <li key={series.key} className="flex items-center gap-2 text-xs text-ink-muted">
              <span
                className="w-4 shrink-0 rounded-full"
                style={{ backgroundColor: series.color, height: `${series.width}px` }}
              />
              {series.label}
            </li>
          ))}
        </ul>
      }
    >
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={points} margin={{ top: 8, right: 8, bottom: 0, left: -22 }}>
          <CartesianGrid stroke={AXIS.stroke} strokeDasharray="2 4" vertical={false} />
          <XAxis
            dataKey="recordedAt"
            tickFormatter={formatDate}
            tick={AXIS.tick}
            stroke={AXIS.stroke}
            minTickGap={28}
          />
          <YAxis domain={[0, 100]} ticks={[0, 50, 100]} tick={AXIS.tick} stroke={AXIS.stroke} />
          <Tooltip content={<ChartTooltip formatLabel={formatDate} />} />

          {[SERIES.ats, SERIES.jobMatch, SERIES.overall].map((series) => (
            <Line
              key={series.key}
              type="monotone"
              dataKey={series.key}
              name={series.label}
              stroke={series.color}
              strokeWidth={series.width}
              dot={false}
              activeDot={{ r: 3, strokeWidth: 0 }}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </ChartFrame>
  )
}
