import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import NewAnalysis from '../NewAnalysis.jsx'
import { runAnalysis } from '../../features/analyses/analysisApi.js'
import { listResumes } from '../../features/resumes/resumeApi.js'
import { listPostings } from '../../features/jobs/jobApi.js'

/**
 * The loop the whole product exists for: pick a resume, pick a posting, get a score.
 *
 * This is the one test that would catch the flow silently breaking — every individual
 * piece can pass its own test while the wizard still fails to hand the two ids to the
 * API, or fails to leave the page afterwards. So the assertions are the two things that
 * must be true at the end: the request carried both ids, and the browser is now on the
 * result.
 *
 * The "cannot continue" assertions matter for the same reason the button is disabled at
 * all: an analysis with no resume is a 400 the user did not need to see.
 */
vi.mock('../../features/analyses/analysisApi.js', () => ({
  runAnalysis: vi.fn(),
  listAnalyses: vi.fn(),
  getAnalysis: vi.fn(),
  deleteAnalysis: vi.fn(),
}))

vi.mock('../../features/resumes/resumeApi.js', () => ({
  listResumes: vi.fn(),
  getResume: vi.fn(),
  uploadResume: vi.fn(),
  deleteResume: vi.fn(),
}))

vi.mock('../../features/jobs/jobApi.js', () => ({
  listPostings: vi.fn(),
  getPosting: vi.fn(),
  savePosting: vi.fn(),
  deletePosting: vi.fn(),
}))

const RESUME = {
  id: 'r1',
  label: 'Backend CV',
  originalFilename: 'rohit-backend.pdf',
  fileSizeBytes: 120_000,
  pageCount: 2,
  wordCount: 480,
  status: 'TEXT_EXTRACTED',
  analysable: true,
  createdAt: '2026-08-20T10:00:00Z',
}

const POSTING = {
  id: 'j1',
  title: 'Backend Developer',
  company: 'Northwind',
  text: 'We are looking for a backend developer with Java and Spring Boot experience.',
  createdAt: '2026-08-21T10:00:00Z',
}

function renderWizard() {
  return render(
    <MemoryRouter initialEntries={['/analyses/new']}>
      <Routes>
        <Route path="/analyses/new" element={<NewAnalysis />} />
        <Route path="/analyses/:id" element={<p>The analysis result page</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('NewAnalysis', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listResumes.mockResolvedValue([RESUME])
    listPostings.mockResolvedValue([POSTING])
  })

  it('will not continue until a resume is chosen', async () => {
    renderWizard()

    await screen.findByText('Backend CV')
    expect(screen.getByRole('button', { name: /Continue/ })).toBeDisabled()

    await userEvent.click(screen.getByRole('radio'))
    expect(screen.getByRole('button', { name: /Continue/ })).toBeEnabled()
  })

  it('runs the analysis with both ids and moves to the result', async () => {
    runAnalysis.mockResolvedValue({ id: 'analysis-1', status: 'COMPLETED', overallScore: 78 })

    renderWizard()

    await screen.findByText('Backend CV')
    await userEvent.click(screen.getByRole('radio'))
    await userEvent.click(screen.getByRole('button', { name: /Continue/ }))

    await screen.findByRole('heading', { name: 'Which job?' })
    await userEvent.click(screen.getByRole('radio'))
    await userEvent.click(screen.getByRole('button', { name: /Continue/ }))

    // The review step repeats both choices back before anything is spent on a model call.
    await screen.findByRole('heading', { name: 'Ready to analyse' })
    expect(screen.getByText('Backend CV')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /Analyse my resume/ }))

    expect(runAnalysis).toHaveBeenCalledWith({ resumeId: 'r1', jobDescriptionId: 'j1' })
    expect(await screen.findByText('The analysis result page')).toBeInTheDocument()
  })

  it('keeps the person on the flow when the run fails', async () => {
    const failure = new Error('We could not read any text from this resume.')
    failure.fieldErrors = {}
    runAnalysis.mockRejectedValue(failure)

    renderWizard()

    await screen.findByText('Backend CV')
    await userEvent.click(screen.getByRole('radio'))
    await userEvent.click(screen.getByRole('button', { name: /Continue/ }))
    await screen.findByRole('heading', { name: 'Which job?' })
    await userEvent.click(screen.getByRole('radio'))
    await userEvent.click(screen.getByRole('button', { name: /Continue/ }))
    await userEvent.click(await screen.findByRole('button', { name: /Analyse my resume/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'We could not read any text from this resume.',
    )
    // Still on step three, with both choices intact, so retrying is one click.
    expect(screen.getByRole('heading', { name: 'Ready to analyse' })).toBeInTheDocument()
  })
})
