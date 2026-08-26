import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { render, screen, within } from '@testing-library/react'
import Demo from '../Demo.jsx'
import { AuthContext } from '../../features/auth/authContext.js'
import { DEMO_ANALYSIS } from '../../features/demo/demoAnalysis.js'

/**
 * The public demo.
 *
 * Three properties are worth locking down here, and they are the three a future phase could
 * quietly break. First, the page says it is sample data *before* it shows a score — a plausible
 * ATS number with no owner named invites the reader to think it is theirs. Second, it renders
 * the whole signed-in report rather than a trimmed version, so the shop window cannot become a
 * description of an older product. Third, it issues no request at all.
 *
 * That last one is asserted rather than described: axios goes through `XMLHttpRequest` under
 * jsdom, so spying on `send` catches any fetch anybody adds to this page later, whichever
 * module it comes from. The demo has to work with the backend switched off, because that is the
 * state a reviewer opening this project for the first time will be in.
 *
 * Region names double as the report's table of contents. Asserting on them means a section
 * silently dropped from `AnalysisReport` fails here as well as on `/analyses/:id`.
 */

const SECTIONS = [
  'Overall match for this role',
  'Requirement by requirement',
  'How the score was reached',
  'Skills you demonstrate',
  'Skill gaps',
  'Keywords',
  'Section by section',
  'What to change',
  'Projects worth building',
  'What to learn next',
]

function renderDemo({ isAuthenticated = false } = {}) {
  const session = {
    status: isAuthenticated ? 'authenticated' : 'anonymous',
    user: isAuthenticated ? { email: 'casey@example.test' } : null,
    isAuthenticated,
    isLoading: false,
    signIn: vi.fn(),
    signUp: vi.fn(),
    signOut: vi.fn(),
  }

  render(
    <MemoryRouter>
      <AuthContext.Provider value={session}>
        <Demo />
      </AuthContext.Provider>
    </MemoryRouter>,
  )
}

const region = (name) => within(screen.getByRole('region', { name }))

describe('Demo', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('says the data is a sample before it shows a single score', () => {
    renderDemo()

    const banner = screen.getByText('This is sample data.')

    expect(screen.getByText(/Nothing on this page was written by a model/)).toBeInTheDocument()

    const verdict = screen.getByRole('region', { name: 'Overall match for this role' })

    expect(banner.compareDocumentPosition(verdict) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('makes no request — the page is a fixture, not a fetch', () => {
    const send = vi.spyOn(window.XMLHttpRequest.prototype, 'send')

    renderDemo()

    expect(screen.getByRole('region', { name: 'Overall match for this role' })).toBeInTheDocument()
    expect(send).not.toHaveBeenCalled()
  })

  it('renders the whole signed-in report, section for section', () => {
    renderDemo()

    SECTIONS.forEach((name) => {
      expect(screen.getByRole('region', { name })).toBeInTheDocument()
    })
  })

  it('carries the fixture score into every meter', () => {
    renderDemo()

    // Scoped to the verdict, because the section block below it has meters of its own —
    // "Keywords" is a headline score there and a region heading here.
    const verdict = region('Overall match for this role')
    const meter = (name) => verdict.getByRole('meter', { name })

    expect(meter('ATS compatibility')).toHaveAttribute(
      'aria-valuenow',
      String(DEMO_ANALYSIS.atsScore),
    )
    expect(meter('Job match')).toHaveAttribute('aria-valuenow', String(DEMO_ANALYSIS.jobMatchScore))
    expect(meter('Skills match')).toHaveAttribute(
      'aria-valuenow',
      String(DEMO_ANALYSIS.skillsMatchScore),
    )
    expect(meter('Experience relevance')).toHaveAttribute(
      'aria-valuenow',
      String(DEMO_ANALYSIS.experienceScore),
    )

    // One section meter, to prove the eight rows are the fixture's and not a placeholder set.
    const summary = DEMO_ANALYSIS.sectionScores.find((row) => row.section === 'SUMMARY')

    expect(
      region('Section by section').getByRole('meter', { name: 'Summary' }),
    ).toHaveAttribute('aria-valuenow', String(summary.score))
  })

  it('shows the unmet requirements as gaps with their evidence', () => {
    renderDemo()

    expect(region('Requirement by requirement').getByText('Docker').closest('li')).toHaveTextContent(
      'Missing',
    )

    const gaps = region('Skill gaps')

    expect(gaps.getByText('Docker')).toBeInTheDocument()
    expect(gaps.getByText(/The resume never mentions it/)).toBeInTheDocument()
    expect(gaps.getByText('AWS')).toBeInTheDocument()
  })

  it('suggests only the absent keywords it can honestly place', () => {
    renderDemo()

    const keywords = region('Keywords')
    const suggestions = within(keywords.getByText('Worth adding, and where').closest('div'))
    const placed = DEMO_ANALYSIS.suggestedKeywords.map((suggestion) => suggestion.term)

    placed.forEach((term) => {
      expect(suggestions.getByText(term)).toBeInTheDocument()
    })
    expect(suggestions.getByText(/GitHub Actions pipeline/)).toBeInTheDocument()

    // The rest are reported as absent and never suggested. That refusal is the product's
    // whole position on keyword stuffing, so the demo has to be caught showing it.
    DEMO_ANALYSIS.missingKeywords
      .filter((term) => !placed.includes(term))
      .forEach((term) => {
        expect(keywords.getByText(term)).toBeInTheDocument()
        expect(suggestions.queryByText(term)).not.toBeInTheDocument()
      })
  })

  it('does not claim a model wrote any of it', () => {
    renderDemo()

    // The report only appends "(offline writer …)" when the document says no model was
    // involved, so the suffix and the fixture flag are one assertion in two places.
    expect(DEMO_ANALYSIS.provenance.modelWritten).toBe(false)
    expect(screen.getByText(/no model was called \(offline writer/)).toBeInTheDocument()
  })

  it('sends an anonymous reader to sign up, and a signed-in one to their dashboard', () => {
    renderDemo()

    expect(screen.getByRole('link', { name: /Run it on mine/ })).toHaveAttribute('href', '/signup')
    expect(screen.getByRole('link', { name: /Create an account/ })).toHaveAttribute(
      'href',
      '/signup',
    )
  })

  it('stays readable for a signed-in visitor', () => {
    renderDemo({ isAuthenticated: true })

    expect(
      within(screen.getByRole('navigation', { name: 'Site' })).getByRole('link', {
        name: 'Open your dashboard',
      }),
    ).toHaveAttribute('href', '/dashboard')
    expect(screen.getByRole('heading', { level: 1, name: 'Sample analysis' })).toBeInTheDocument()
  })
})
