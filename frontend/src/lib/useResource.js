import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Loads something from the API when a component mounts, and again when asked.
 *
 * This is the whole data layer. React Query would give caching, deduplication and
 * background refetching, none of which this app needs: every screen loads one or two
 * resources, a stale dashboard is corrected by the reload the user already expects
 * after running an analysis, and the spec's dependency list is the budget. Seventy
 * lines that the next reader can hold in their head beat a library whose behaviour
 * they would have to look up.
 *
 * Two hazards it exists to handle.
 *
 * **Stale responses.** A slow first request must not overwrite a fast second one. The
 * effect closes over `active`, so a response that arrives after its effect was torn
 * down is dropped rather than rendered — the bug where switching filters twice leaves
 * the first filter's data on screen.
 *
 * **Re-render loops.** The loader is almost always an inline arrow function, which is
 * a new value on every render, so using it as an effect dependency would fetch
 * forever. It is kept in a ref instead and the effect is keyed on the `deps` the
 * caller declares. That is the same contract as `useEffect` — declare what the load
 * depends on — and it is why `deps` is not optional in spirit even though it defaults
 * to empty.
 *
 * Errors arrive already normalised: the axios interceptor rejects with an `ApiError`,
 * so `error.message` is safe to render and no caller touches `error.response`.
 *
 * @param {() => Promise<unknown>} loader called with no arguments, returns the data
 * @param {unknown[]} deps values the load depends on, like a route parameter
 */
export function useResource(loader, deps = []) {
  const [state, setState] = useState({ status: 'loading', data: null, error: null })
  const [reloadToken, setReloadToken] = useState(0)

  const loaderRef = useRef(loader)
  loaderRef.current = loader

  useEffect(() => {
    let active = true
    setState((current) => ({ ...current, status: 'loading', error: null }))

    loaderRef.current()
      .then((data) => {
        if (active) setState({ status: 'ready', data, error: null })
      })
      .catch((error) => {
        if (active) setState({ status: 'failed', data: null, error })
      })

    return () => {
      active = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reloadToken, ...deps])

  const reload = useCallback(() => setReloadToken((token) => token + 1), [])

  /**
   * Replaces the loaded data without a round trip.
   *
   * For the one case that would otherwise need one: a list that just deleted a row
   * knows exactly what it should now contain, and re-fetching to learn that produces
   * a visible flicker for no new information.
   */
  const setData = useCallback((update) => {
    setState((current) => ({
      ...current,
      data: typeof update === 'function' ? update(current.data) : update,
    }))
  }, [])

  return {
    ...state,
    isLoading: state.status === 'loading',
    isReady: state.status === 'ready',
    hasFailed: state.status === 'failed',
    reload,
    setData,
  }
}
