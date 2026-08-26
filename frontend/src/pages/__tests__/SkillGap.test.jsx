import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import SkillGap from '../SkillGap.jsx'
import { getAnalysis, listAnalyses } from '../../features/analyses/analysisApi.js'
import { fetchDashboard } from '../../features/dashboard/dashboardApi.js'

/**
 * Three behaviours that would each quietly ruin this page.
 *
 * The picker must default to the newest completed run, because a page that opens on an
 * arbitrary analysis looks broken to anyone who has run more than one. It must not offer a
 * failed run, which has no skills and would render a blank detail block. And changing the
 * selection must actually refetch — the `useResource` dependency array is the only thing
 * making that happen, and a missing dependency is invisible until the day someone switches.
 *
 * Every assertion goes through the tables ChartFrame renders rather than the SVG, which is
 * the only honest thing to assert in jsdom and also what a screen reader gets.
 */
vi.mock('../../features/analyses/analysisApi.js', () => ({
  listAnalyses: vi.fn(),
  getAnalysis: vi.fn(),
}))

vi.mock('../../features/dashboard/dashboardApi.js', () => ({ fetchDashboard: vi.fn() }))

const HISTORY = [
  {
    id: 'a2',
    status: 'COMPLETED',
    jobTitle: 'Backend Developer',
    createdAt: '2026-08-20T10:00:00Z',
  },
  {
    id: 'a1',
    status: 'COMPLETED',
    jobTitle: 'Platform Engineer',
    createdAt: '2026-07-02T10:00:00Z',
  },
  { id: 'a0', status: 'FAILED', jobTitle: 'Broken run', createdAt: '2026-06-01T10:00:00Z' },
]

const ANALYSES = {
  a2: {
    id: 'a2',
    detectedSkills: [{ name: 'Java 17', status: 'STRONG', importance: 'CRITICAL' }],
    missingSkills: [{ name: 'Docker', status: 'MISSING', importance: 'CRITICAL' }],
    sectionScores: [
      { section: 'EXPERIENCE', score: 80, note: 'Two roles with outcomes.' },
      { section: 'SUMMARY', score: 55, note: 'Generic opening line.' },
      { section: 'PROJECTS', score: 72, note: 'Three projects.' },
    ],
  },
  a1: {
    id: 'a1',
    detectedSkills: [{ name: 'Linux', status: 'PARTIAL', importance: 'IMPORTANT' }],
    missingSkills: [{ name: 'Terraform', status: 'MISSING', importance: 'IMPORTANT' }],
    sectionScores: [
      { section: 'CERTIFICATIONS', score: 20, note: 'None listed.' },
      { section: 'SKILLS', score: 61, note: 'Long, unordered list.' },
      { section: 'FORMATTING', score: 88, note: 'Parses cleanly.' },
    ],
  },
}

const DASHBOARD = {
  counts: { analyses: 3, resumes: 1, jobDescriptions: 3 },
  scores: { average: 68, best: 74, latest: 74 },
  scoreHistory: [],
  recentAnalyses: [],
  topSkillGaps: [
    { skill: 'Docker', occurrences: 3 },
    { skill: 'AWS', occurrences: 1 },
  ],
}

function renderSkillGap() {
  return render(
    <MemoryRouter>
      <SkillGap />
    </MemoryRouter>,
  )
}

describe('SkillGap', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    fetchDashboard.mockResolvedValue(DASHBOARD)
    listAnalyses.mockResolvedValue(HISTORY)
    getAnalysis.mockImplementation((id) => Promise.resolve(ANALYSES[id]))
  })

  it('counts the gaps that repeat across every analysis', async () => {
    renderSkillGap()

    const table = await screen.findByRole('table', { name: 'Gaps that keep coming back' })
    const docker = within(table).getByRole('rowheader', { name: 'Docker' }).closest('tr')
    expect(within(docker).getByRole('cell')).toHaveTextContent('3')
  })

  it('opens on the newest completed run and never offers a failed one', async () => {
    renderSkillGap()

    await waitFor(() => expect(getAnalysis).toHaveBeenCalledWith('a2'))
    expect(screen.getByLabelText('Analysis')).toHaveValue('a2')
    expect(screen.queryByRole('option', { name: /Broken run/ })).not.toBeInTheDocument()
    expect(screen.getAllByRole('option')).toHaveLength(2)
  })

  it('refetches when another analysis is chosen', async () => {
    renderSkillGap()

    // The newest run's sections, before switching.
    expect(await screen.findByRole('rowheader', { name: 'Experience' })).toBeInTheDocument()

    await userEvent.selectOptions(await screen.findByLabelText('Analysis'), 'a1')

    await waitFor(() => expect(getAnalysis).toHaveBeenCalledWith('a1'))
    expect(await screen.findByRole('rowheader', { name: 'Certifications' })).toBeInTheDocument()
    expect(screen.queryByRole('rowheader', { name: 'Experience' })).not.toBeInTheDocument()
  })

  it('lists the unmet requirements for the selected run', async () => {
    renderSkillGap()

    const rail = await screen.findByRole('list')
    expect(within(rail).getByText('Docker')).toBeInTheDocument()
    // Only the gaps: a skill the resume already proves has no place on a gap sheet.
    expect(within(rail).queryByText('Java 17')).not.toBeInTheDocument()
  })

  it('asks for an analysis before showing gaps', async () => {
    listAnalyses.mockResolvedValue([HISTORY[2]])

    renderSkillGap()

    expect(await screen.findByText('No completed analyses yet')).toBeInTheDocument()
    expect(getAnalysis).not.toHaveBeenCalled()
  })
})
