import { describe, expect, it } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import ChartFrame from '../ChartFrame.jsx'

/**
 * The frame is where this app's charts become testable at all.
 *
 * Recharts draws into an SVG that jsdom never lays out, so an assertion on a `<path>` would
 * be an assertion on nothing. That is not a testing inconvenience to work around — it is the
 * same reason a chart is useless to a screen reader, and the reason ChartFrame demands a
 * table. Test the table and you are testing what a person who cannot see the picture gets.
 *
 * So: the numbers are reachable as data, the drawing is hidden from assistive technology,
 * and the caption names the figure whether or not it is visible.
 */
function renderFrame(props) {
  return render(
    <ChartFrame
      title="Coverage by importance"
      lead="How the posting weighted each requirement."
      columns={['Importance', 'Shown', 'Missing']}
      rows={[
        ['Critical', 4, 2],
        ['Nice to have', 1, 0],
      ]}
      {...props}
    >
      <div>the drawing</div>
    </ChartFrame>,
  )
}

describe('ChartFrame', () => {
  it('publishes every number as a table', () => {
    renderFrame()

    const table = screen.getByRole('table', { name: 'Coverage by importance' })
    expect(within(table).getByRole('columnheader', { name: 'Missing' })).toBeInTheDocument()

    const critical = within(table).getByRole('rowheader', { name: 'Critical' }).closest('tr')
    expect(within(critical).getAllByRole('cell').map((cell) => cell.textContent)).toEqual(['4', '2'])
  })

  it('hides the drawing from assistive technology', () => {
    renderFrame()

    // Not "does the SVG render" — whether the picture is announced, which is the part that
    // would otherwise read as a list of unlabelled paths.
    expect(screen.getByText('the drawing').closest('[aria-hidden="true"]')).not.toBeNull()
  })

  it('names the figure with its title', () => {
    renderFrame()

    expect(screen.getByRole('figure', { name: 'Coverage by importance' })).toBeInTheDocument()
    expect(screen.getByText('How the posting weighted each requirement.')).toBeVisible()
  })

  it('keeps the accessible name when the caption is hidden', () => {
    renderFrame({ captionHidden: true })

    // A panel that already carries the heading hides the caption visually; the figure and
    // the table must still be named by it, or the sr-only table loses its only label.
    expect(screen.getByRole('figure', { name: 'Coverage by importance' })).toBeInTheDocument()
    expect(screen.getByRole('table', { name: 'Coverage by importance' })).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { level: 3, name: 'Coverage by importance' }).closest('figcaption'),
    ).toHaveClass('sr-only')
  })

  it('leaves the legend out of the accessibility tree', () => {
    renderFrame({ legend: <p>Shown / Missing</p> })

    // The legend repeats what the table already says, so it is decoration for the eye only.
    expect(screen.getByText('Shown / Missing').closest('[aria-hidden="true"]')).not.toBeNull()
  })
})
