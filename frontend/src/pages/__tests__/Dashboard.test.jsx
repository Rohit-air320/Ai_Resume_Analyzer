import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import Dashboard from '../Dashboard.jsx'
import { fetchDashboard } from '../../features/dashboard/dashboardApi.js'

/**
 * The dashboard's two jobs, tested.
 *
 * First, that a brand-new account gets an invitation rather than a wall of zeroes — the
 * empty state is the most persuasive screen in the product and the easiest one to leave
 * broken, because nobody with data ever sees it again.
 *
 * Second, that an absent score renders as a dash. The API omits `average`, `best` and
 * `latest` entirely until there is something to average, and the failure this guards
 * against is a card confidently displaying 0 out of 100 — a fabricated verdict, which is
 * exactly what this product argues against.
 */
vi.mock('../../features/dashboard/dashboardApi.js', () => ({ fetchDashboard: vi.fn() }))

vi.mock('../../features/auth/authContext.js', () => ({
  useAuth: () => ({ user: { fullName: 'Rohit Sharma', email: 'rohit@example.com' } }),
}))

function renderDashboard() {
  return render(
    <MemoryRouter>
      <Dashboard />
    </MemoryRouter>,
  )
}

describe('Dashboard', () => {
  beforeEach(() => vi.clearAllMocks())

  it('invites a first analysis when there is nothing to show', async () => {
    fetchDashboard.mockResolvedValue({
      counts: { analyses: 0, resumes: 0, jobDescriptions: 0 },
      scores: {},
      scoreHistory: [],
      recentAnalyses: [],
      topSkillGaps: [],
    })

    renderDashboard()

    expect(await screen.findByText('No analyses yet')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Run your first analysis' })).toHaveAttribute(
      'href',
      '/analyses/new',
    )
    // No metric cards, so no chance of showing a zero that looks like a verdict.
    expect(screen.queryByText('Best score')).not.toBeInTheDocument()
  })

  it('shows the scores, the gaps and the recent runs', async () => {
    fetchDashboard.mockResolvedValue({
      counts: { analyses: 4, resumes: 2, jobDescriptions: 3 },
      scores: { average: 71, best: 84, latest: 78 },
      scoreHistory: [
        { recordedAt: '2026-08-01T10:00:00Z', overall: 62, ats: 70, jobMatch: 58 },
        { recordedAt: '2026-08-20T10:00:00Z', overall: 78, ats: 84, jobMatch: 74 },
      ],
      recentAnalyses: [
        {
          id: 'a1',
          status: 'COMPLETED',
          overallScore: 66,
          atsScore: 70,
          jobMatchScore: 61,
          jobTitle: 'Backend Developer',
          company: 'Northwind',
          resumeLabel: 'Backend CV',
          createdAt: '2026-08-20T10:00:00Z',
        },
      ],
      topSkillGaps: [
        { skill: 'Docker', occurrences: 3 },
        { skill: 'AWS', occurrences: 1 },
      ],
      targetRole: 'Backend Developer',
    })

    renderDashboard()

    expect(await screen.findByRole('heading', { name: 'Welcome back, Rohit' })).toBeInTheDocument()
    expect(screen.getByText('Aiming at Backend Developer')).toBeInTheDocument()

    expect(screen.getByText('84')).toBeInTheDocument()
    expect(screen.getByText('71')).toBeInTheDocument()

    expect(screen.getByText('Docker')).toBeInTheDocument()
    expect(screen.getByText('3×')).toBeInTheDocument()

    expect(screen.getByRole('link', { name: /Backend Developer · Northwind/ })).toHaveAttribute(
      'href',
      '/analyses/a1',
    )
  })

  it('renders a dash instead of a zero for a score the API omitted', async () => {
    fetchDashboard.mockResolvedValue({
      counts: { analyses: 1, resumes: 1, jobDescriptions: 1 },
      // A single failed analysis: it counts, but it produced no numbers to average.
      scores: {},
      scoreHistory: [],
      recentAnalyses: [],
      topSkillGaps: [],
    })

    renderDashboard()

    await waitFor(() => expect(screen.getByText('Best score')).toBeInTheDocument())
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(3)
    expect(screen.queryByText('0')).not.toBeInTheDocument()
  })
})
