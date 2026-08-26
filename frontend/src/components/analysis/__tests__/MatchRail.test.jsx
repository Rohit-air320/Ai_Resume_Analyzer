import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import MatchRail from '../MatchRail.jsx'

/**
 * The rail is the product's signature element, so the thing worth protecting is its
 * argument rather than its appearance: requirements grouped by how much the posting cared,
 * and inside a group the unmet ones first. Get that ordering wrong and the rail becomes a
 * decorative list — the reader's eye lands on a nice-to-have they already have instead of
 * on the critical requirement they do not.
 */
const DETECTED = [
  { name: 'Java 17', status: 'STRONG', importance: 'CRITICAL' },
  { name: 'MySQL', status: 'PARTIAL', importance: 'IMPORTANT' },
  { name: 'Git', status: 'STRONG', importance: 'NICE_TO_HAVE' },
]

const MISSING = [
  { name: 'Docker', status: 'MISSING', importance: 'CRITICAL' },
  { name: 'Kafka', status: 'MISSING', importance: 'NICE_TO_HAVE' },
]

function rowText() {
  return screen.getAllByRole('listitem').map((row) => row.textContent)
}

describe('MatchRail', () => {
  it('groups by importance and puts unmet requirements first', () => {
    render(<MatchRail detected={DETECTED} missing={MISSING} />)

    expect(rowText()).toEqual([
      'DockerMissing',
      'Java 17Strong',
      'MySQLPartial',
      'KafkaMissing',
      'GitStrong',
    ])

    // Group order follows the posting's emphasis, not the API's array order.
    const headings = screen.getAllByText(/^(Critical|Important|Nice to have)$/)
    expect(headings.map((node) => node.textContent)).toEqual([
      'Critical',
      'Important',
      'Nice to have',
    ])
  })

  it('draws a missing requirement with a broken connector', () => {
    const { container } = render(<MatchRail missing={MISSING} />)

    // The dashed line is the metaphor doing the work: the connection was never made.
    const connectors = container.querySelectorAll('li > span[aria-hidden="true"]')
    expect(connectors).toHaveLength(2)
    connectors.forEach((connector) => expect(connector.className).toContain('border-dashed'))
  })

  it('leaves out an importance the posting never used', () => {
    render(<MatchRail detected={[DETECTED[0]]} missing={[]} />)

    expect(screen.getByText('Critical')).toBeInTheDocument()
    expect(screen.queryByText('Important')).not.toBeInTheDocument()
    expect(screen.queryByText('Nice to have')).not.toBeInTheDocument()
  })

  it('keeps a requirement whose importance the API did not name', () => {
    render(<MatchRail missing={[{ name: 'Terraform', status: 'MISSING' }]} />)

    // Dropping it would be the worst outcome: a gap the user never sees because an enum
    // grew a value the client did not know about.
    expect(screen.getByText('Also mentioned')).toBeInTheDocument()
    expect(screen.getByText('Terraform')).toBeInTheDocument()
  })

  it('renders nothing when there is nothing to compare', () => {
    const { container } = render(<MatchRail />)

    expect(container).toBeEmptyDOMElement()
  })
})
