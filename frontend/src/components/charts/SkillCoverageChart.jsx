import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import ChartFrame from './ChartFrame.jsx'
import ChartTooltip from './ChartTooltip.jsx'
import { AXIS, token } from '../../lib/chartTokens.js'
import { humanise } from '../../lib/format.js'

/**
 * Coverage by how much the requirement matters.
 *
 * Counting skills is close to useless — "you have 11 and are missing 4" says nothing about
 * whether the 4 are the ones that get a resume rejected. Splitting the same counts by the
 * posting's own emphasis is the whole point: two missing critical requirements is a worse
 * position than six missing nice-to-haves, and this is the only view in the product that
 * makes that comparison visible at a glance.
 *
 * Bars run horizontally because the labels are words. A vertical bar chart with three
 * multi-word categories either rotates its labels to 45 degrees or truncates them, and
 * both are worse than turning the chart on its side.
 *
 * An importance level the posting never used is dropped rather than drawn as an empty
 * row, so the chart has two bars when the posting only distinguished two levels.
 *
 * @param {object} props
 * @param {Array<{importance: string}>} props.detected
 * @param {Array<{importance: string}>} props.missing
 */

const LEVELS = ['CRITICAL', 'IMPORTANT', 'NICE_TO_HAVE']
const SHOWN = token('band-strong')
const ABSENT = token('band-critical')

export default function SkillCoverageChart({ detected = [], missing = [] }) {
  const tally = LEVELS.map((level) => ({
    level,
    label: humanise(level),
    shown: detected.filter((skill) => skill.importance === level).length,
    absent: missing.filter((skill) => skill.importance === level).length,
  })).filter((row) => row.shown + row.absent > 0)

  if (tally.length === 0) return null

  const widest = Math.max(...tally.map((row) => row.shown + row.absent))

  return (
    <ChartFrame
      title="Coverage by importance"
      lead="How the posting weighted each requirement, against whether your resume shows it."
      height={tally.length * 56 + 44}
      columns={['Importance', 'Shown', 'Missing']}
      rows={tally.map((row) => [row.label, row.shown, row.absent])}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart
          data={tally}
          layout="vertical"
          margin={{ top: 0, right: 12, bottom: 0, left: 8 }}
          barSize={16}
        >
          <CartesianGrid stroke={AXIS.stroke} strokeDasharray="2 4" horizontal={false} />
          <XAxis
            type="number"
            domain={[0, widest]}
            allowDecimals={false}
            tick={AXIS.tick}
            stroke={AXIS.stroke}
          />
          <YAxis
            type="category"
            dataKey="label"
            width={92}
            tick={AXIS.tick}
            stroke={AXIS.stroke}
          />
          <Tooltip content={<ChartTooltip />} cursor={{ fill: token('surface-sunken', 0.6) }} />
          <Legend
            iconType="square"
            iconSize={8}
            wrapperStyle={{ fontSize: 11, color: token('ink-muted') }}
          />

          <Bar dataKey="shown" name="Shown" stackId="skills" fill={SHOWN} />
          <Bar dataKey="absent" name="Missing" stackId="skills" fill={ABSENT} radius={[0, 3, 3, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </ChartFrame>
  )
}
