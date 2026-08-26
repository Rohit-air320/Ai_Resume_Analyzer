import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useResource } from '../useResource.js'

/**
 * Every screen's loading, error and retry behaviour comes from this hook, so these tests
 * are really about three bugs it exists to prevent.
 *
 * The first is the loop: a page that passes an inline arrow — which every page does — would
 * re-run the loader on every render if the effect depended on the function identity. The
 * "loads once" test fails loudly if that regresses.
 *
 * The second is the stale response overwriting a fresh one. The last test resolves two
 * requests out of order and asserts the abandoned one is dropped, which is what the
 * `active` flag is for.
 *
 * The third is a failure that hides the retry: `hasFailed` and `reload` have to work
 * together, or a network blip becomes a dead page.
 */
function Probe({ loader, deps }) {
  const resource = useResource(loader, deps)

  return (
    <div>
      <p data-testid="status">{resource.status}</p>
      <p data-testid="data">{resource.data ?? 'none'}</p>
      <p data-testid="error">{resource.error?.message ?? 'none'}</p>
      <button type="button" onClick={resource.reload}>
        Reload
      </button>
      <button type="button" onClick={() => resource.setData('edited locally')}>
        Edit
      </button>
    </div>
  )
}

describe('useResource', () => {
  it('starts loading, then exposes the value', async () => {
    render(<Probe loader={() => Promise.resolve('a dashboard')} deps={[]} />)

    expect(screen.getByTestId('status')).toHaveTextContent('loading')
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('ready'))
    expect(screen.getByTestId('data')).toHaveTextContent('a dashboard')
  })

  it('calls the loader once even though the caller passes a new function every render', async () => {
    const load = vi.fn(() => Promise.resolve('once'))
    const { rerender } = render(<Probe loader={() => load()} deps={[]} />)

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('ready'))
    rerender(<Probe loader={() => load()} deps={[]} />)
    rerender(<Probe loader={() => load()} deps={[]} />)

    expect(load).toHaveBeenCalledTimes(1)
  })

  it('surfaces a failure and recovers on reload', async () => {
    const load = vi
      .fn()
      .mockRejectedValueOnce(new Error('The server is not answering'))
      .mockResolvedValueOnce('second time lucky')

    render(<Probe loader={() => load()} deps={[]} />)

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('failed'))
    expect(screen.getByTestId('error')).toHaveTextContent('The server is not answering')

    await userEvent.click(screen.getByRole('button', { name: 'Reload' }))

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('ready'))
    expect(screen.getByTestId('data')).toHaveTextContent('second time lucky')
    expect(screen.getByTestId('error')).toHaveTextContent('none')
  })

  it('lets a caller edit the loaded value without a refetch', async () => {
    const load = vi.fn(() => Promise.resolve('from the server'))
    render(<Probe loader={() => load()} deps={[]} />)

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('ready'))
    await userEvent.click(screen.getByRole('button', { name: 'Edit' }))

    expect(screen.getByTestId('data')).toHaveTextContent('edited locally')
    expect(load).toHaveBeenCalledTimes(1)
  })

  it('drops a response that arrives after the request it replaced', async () => {
    let resolveFirst
    const load = vi
      .fn()
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveFirst = resolve
      }))
      .mockImplementationOnce(() => Promise.resolve('for the second id'))

    const { rerender } = render(<Probe loader={() => load()} deps={['first']} />)
    rerender(<Probe loader={() => load()} deps={['second']} />)

    await waitFor(() => expect(screen.getByTestId('data')).toHaveTextContent('for the second id'))

    // The abandoned request answers last. Without the guard this would win.
    resolveFirst('for the first id')
    await waitFor(() => expect(screen.getByTestId('data')).toHaveTextContent('for the second id'))
  })
})
