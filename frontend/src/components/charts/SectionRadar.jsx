import {
  PolarAngleAxis,
  PolarGrid,
  PolarRadiusAxis,
  Radar,
  RadarChart,
  ResponsiveContainer,
  Tooltip,
} from 'recharts'
import ChartFrame from './ChartFrame.jsx'
import ChartTooltip from './ChartTooltip.jsx'
import { AXIS, token } from '../../lib/chartTokens.js'
import { humanise } from '../../lib/format.js'

/**
 * The shape of a resume, section by section.
 *
 * A radar is the wrong chart most of the time — it compares areas the eye cannot compare,
 * and it invites a reading of "bigger is better" that is often meaningless. It earns its
 * place here for one reason: these eight axes are the sections of one document, all scored
 * on the same 0-100 scale, and the useful question is which of them is dented. A dent in an
 * outline is the thing peripheral vision is actually good at spotting, which is more than
 * can be said for eight bars.
 *
 * Radius is pinned to 0-100 for the same reason the trend chart's y-axis is: a fitted
 * radius turns a resume scoring 70 to 80 everywhere into a dramatic, meaningless spike.
 *
 * The axis labels are the API's own section names, humanised — not a second list of
 * captions that would need updating whenever the backend adds a section.
 *
 * @param {object} props
 * @param {Array<{section: string, score: number, note: string}>} props.sections
 */
export default function SectionRadar({ sections }) {
  if (!sections || sections.length < 3) return null

  const data = sections.map((entry) => ({
    label: humanise(entry.section),
    score: entry.score,
  }))

  return (
    <ChartFrame
      title="Section shape"
      lead="Every section on the same scale, so the dent is the thing to fix."
      height={300}
      columns={['Section', 'Score']}
      rows={sections.map((entry) => [humanise(entry.section), entry.score])}
    >
      <ResponsiveContainer width="100%" height="100%">
        <RadarChart data={data} outerRadius="72%">
          <PolarGrid stroke={AXIS.stroke} />
          <PolarAngleAxis dataKey="label" tick={AXIS.tick} />
          <PolarRadiusAxis domain={[0, 100]} tick={false} axisLine={false} />
          <Tooltip content={<ChartTooltip />} />
          <Radar
            name="Score"
            dataKey="score"
            stroke={token('brand-600')}
            strokeWidth={2}
            fill={token('brand-600')}
            fillOpacity={0.16}
          />
        </RadarChart>
      </ResponsiveContainer>
    </ChartFrame>
  )
}
