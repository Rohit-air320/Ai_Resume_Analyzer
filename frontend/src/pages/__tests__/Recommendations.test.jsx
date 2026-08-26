import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import Recommendations from '../Recommendations.jsx'
import { listRecommendations } from '../../features/recommendations/recommendationApi.js'

/**
 * What this page can get wrong without looking broken.
 *
 * The filter is the whole risk. Filtering in the browser would look identical on a small
 * account and quietly lie on a large one, because the endpoint stops at 100 rows — so the
 * test asserts on the *request*, not on which cards happen to be visible. Anything less and
 * a well-meaning refactor to `items.filter(...)` would pass.
 *
 * The second risk is advice that has lost its origin. "Learn Docker" with no posting behind
 * it is an opinion, so every row must still link back to the analysis that produced it.
 */
vi.mock('../../features/recommendations/recommendationApi.js', () => ({
  listRecommendations: vi.fn(),
}))

const LEARNING = {
  analysisId: 'a2',
  type: 'LEARNING',
  title: 'Learn container basics',
  detail: 'Two of the postings you targeted named Docker as a requirement.',
  priority: 'HIGH',
  resourceUrl: 'https://docs.docker.com/get-started/',
  jobTitle: 'Backend Developer',
  createdAt: '2026-08-20T10:00:00Z',
}

const IMPROVEMENT = {
  analysisId: 'a1',
  type: 'IMPROVEMENT',
  title: 'Put numbers on the payments project',
  detail: 'The project reads as a description. Say what it handled and how much.',
  priority: 'MEDIUM',
  resourceUrl: null,
  jobTitle: 'Platform Engineer',
  createdAt: '2026-07-02T10:00:00Z',
}

function renderRecommendations() {
  return render(
    <MemoryRouter>
      <Recommendations />
    </MemoryRouter>,
  )
}

describe('Recommendations', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listRecommendations.mockResolvedValue([LEARNING, IMPROVEMENT])
  })

  it('shows each suggestion with the posting that prompted it', async () => {
    renderRecommendations()

    const card = (await screen.findByText(LEARNING.title)).closest('li')
    expect(within(card).getByText(LEARNING.detail)).toBeInTheDocument()
    expect(within(card).getByText('Learning')).toBeInTheDocument()
    expect(within(card).getByText('High')).toBeInTheDocument()
    expect(within(card).getByRole('link', { name: 'From Backend Developer' })).toHaveAttribute(
      'href',
      '/analyses/a2',
    )

    // Server order is the product's claim: this is a history, not a priority queue.
    expect(screen.getAllByRole('listitem').map((row) => row.querySelector('h2').textContent)).toEqual(
      [LEARNING.title, IMPROVEMENT.title],
    )
  })

  it('asks the server for one kind instead of filtering what it already fetched', async () => {
    renderRecommendations()

    await waitFor(() => expect(listRecommendations).toHaveBeenCalledWith(null))
    listRecommendations.mockResolvedValue([LEARNING])

    await userEvent.click(screen.getByRole('button', { name: 'Learn' }))

    // The request is the assertion. A client-side filter would pass every other check here.
    await waitFor(() => expect(listRecommendations).toHaveBeenCalledWith('LEARNING'))
    expect(await screen.findByText(LEARNING.title)).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByText(IMPROVEMENT.title)).not.toBeInTheDocument())

    expect(screen.getByRole('button', { name: 'Learn' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Everything' })).toHaveAttribute(
      'aria-pressed',
      'false',
    )
  })

  it('links out only for the suggestions that carry a resource', async () => {
    renderRecommendations()

    const links = await screen.findAllByRole('link', { name: 'Where to learn it' })
    expect(links).toHaveLength(1)
    expect(links[0]).toHaveAttribute('href', LEARNING.resourceUrl)
    // A new tab that can read back into this one is the leak nobody notices.
    expect(links[0]).toHaveAttribute('rel', expect.stringContaining('noreferrer'))
  })

  it('still links to the analysis when the posting had no title', async () => {
    listRecommendations.mockResolvedValue([{ ...IMPROVEMENT, jobTitle: null }])

    renderRecommendations()

    // Losing the link would strand the advice; "From null" would be worse than losing it.
    expect(await screen.findByRole('link', { name: 'Open the analysis' })).toHaveAttribute(
      'href',
      '/analyses/a1',
    )
  })

  it('explains an empty list differently once a filter is on', async () => {
    listRecommendations.mockResolvedValue([])

    renderRecommendations()

    expect(await screen.findByText('No recommendations yet')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Keywords' }))

    // "Run an analysis" is wrong advice for someone who has run six and filtered badly.
    expect(await screen.findByText('Nothing of this kind yet')).toBeInTheDocument()
    expect(screen.queryByText('No recommendations yet')).not.toBeInTheDocument()
  })
})
