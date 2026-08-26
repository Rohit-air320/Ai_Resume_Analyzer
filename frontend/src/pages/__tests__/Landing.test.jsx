import { describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { render, screen, within } from '@testing-library/react'
import Landing from '../Landing.jsx'
import { AuthContext } from '../../features/auth/authContext.js'
import { DEMO_ANALYSIS } from '../../features/demo/demoAnalysis.js'
import { SCORE_BANDS, labelForScore } from '../../lib/scoreBands.js'

/**
 * The landing page, checked against the two modules it claims to be showing.
 *
 * A marketing page is the easiest file in a codebase to let rot, because nothing breaks when
 * it starts describing an older product. So almost nothing here is asserted against a string
 * typed into the test: the scores come from `DEMO_ANALYSIS`, the band labels and thresholds
 * come from `SCORE_BANDS`, and the verdict word comes from `labelForScore`. Change the fixture
 * or the scale and these tests follow; hard-code a number into the page and they fail.
 *
 * The session is supplied through `AuthContext` rather than a module mock, the same way
 * `Login.test.jsx` does it — the header adapts to a session, and the point worth proving is
 * that adapting is *all* it does. No redirect, either way.
 */

function renderLanding({ isAuthenticated = false } = {}) {
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
        <Landing />
      </AuthContext.Provider>
    </MemoryRouter>,
  )
}

const siteNav = () => within(screen.getByRole('navigation', { name: 'Site' }))

describe('Landing', () => {
  it('shows the real match rail on the shared fixture, not a picture of one', () => {
    renderLanding()

    // The pill's word is whatever the scoring module calls 82 — not an adjective in the copy.
    const pill = screen.getByText(String(DEMO_ANALYSIS.overallScore)).parentElement
    expect(pill).toHaveTextContent(labelForScore(DEMO_ANALYSIS.overallScore))

    // Two rail rows: one requirement the resume proves, one it does not. Both are rendered by
    // the same component the results page uses, so a visitor is looking at the product.
    expect(screen.getByText('Spring Boot').closest('li')).toHaveTextContent('Strong')
    expect(screen.getByText('Docker').closest('li')).toHaveTextContent('Missing')
  })

  it('reads its headline numbers out of the fixture', () => {
    renderLanding()

    const metric = (label) => screen.getByText(label).closest('div')

    expect(metric('ATS score')).toHaveTextContent(String(DEMO_ANALYSIS.atsScore))
    expect(metric('Job match')).toHaveTextContent(String(DEMO_ANALYSIS.jobMatchScore))
    // Counted, not stated: a gap added to the fixture has to show up here.
    expect(metric('Gaps found')).toHaveTextContent(String(DEMO_ANALYSIS.missingSkills.length))
  })

  it('publishes every band from the scale the app actually scores with', () => {
    renderLanding()

    expect(SCORE_BANDS).toHaveLength(5)

    SCORE_BANDS.forEach((band) => {
      const row = screen.getByText(`${band.min}–${band.max}`).closest('li')

      expect(row).toHaveTextContent(band.label)
    })
  })

  it('offers the sample and both ways in when nobody is signed in', () => {
    renderLanding()

    expect(screen.getByRole('link', { name: 'Read a sample analysis' })).toHaveAttribute(
      'href',
      '/demo',
    )
    expect(screen.getByRole('link', { name: 'Analyse my resume' })).toHaveAttribute(
      'href',
      '/signup',
    )
    expect(siteNav().getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login')
    expect(siteNav().getByRole('link', { name: 'Create account' })).toHaveAttribute(
      'href',
      '/signup',
    )
  })

  it('swaps the header call to action for a signed-in visitor without redirecting them', () => {
    renderLanding({ isAuthenticated: true })

    expect(siteNav().getByRole('link', { name: 'Open your dashboard' })).toHaveAttribute(
      'href',
      '/dashboard',
    )
    expect(siteNav().queryByRole('link', { name: 'Sign in' })).not.toBeInTheDocument()
    expect(siteNav().queryByRole('link', { name: 'Create account' })).not.toBeInTheDocument()

    // Still the landing page. The people who can check whether the copy is true are the ones
    // with an account, and bouncing them to the dashboard would put it out of reach.
    expect(
      screen.getByRole('heading', { level: 1, name: /Read your resume the way the posting/ }),
    ).toBeInTheDocument()
  })

  it('says what the product will not do, including where the provider key lives', () => {
    renderLanding()

    expect(screen.getByText(/no truthful placement is dropped, not padded in/)).toBeInTheDocument()
    expect(screen.getByText(/Invent experience, skills or certifications/)).toBeInTheDocument()
    expect(screen.getByText(/key lives on the server/)).toBeInTheDocument()
  })
})
