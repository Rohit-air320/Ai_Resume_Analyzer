import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { StrictMode } from 'react'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AuthProvider } from '../AuthProvider.jsx'
import { useAuth } from '../authContext.js'
import { login, logout, refresh } from '../authApi.js'
import { setAuthTokenProvider, setSessionRecovery } from '../../../lib/apiClient.js'

/**
 * The session, as the rest of the app experiences it.
 *
 * Two collaborators are mocked and the reason differs for each. `authApi` is mocked
 * because these tests are about what the provider does with an answer, not about
 * HTTP. `setAuthTokenProvider` and `setSessionRecovery` are mocked because they are
 * the provider's output, not its input: capturing what it registers is the only way
 * to assert on the token without exposing it through the context, which is precisely
 * what the production code refuses to do.
 */
vi.mock('../authApi.js', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  refresh: vi.fn(),
  register: vi.fn(),
  fetchCurrentUser: vi.fn(),
}))

vi.mock('../../../lib/apiClient.js', async () => {
  const actual = await vi.importActual('../../../lib/apiClient.js')
  return { ...actual, setAuthTokenProvider: vi.fn(), setSessionRecovery: vi.fn() }
})

const USER = { id: 'c0ffee00-0000-4000-8000-000000000001', email: 'casey@example.test', fullName: 'Casey Rivers' }
const RESTORED_TOKEN = 'restored.access.token'
const SIGNED_IN_TOKEN = 'signed-in.access.token'
const RENEWED_TOKEN = 'renewed.access.token'

const session = (accessToken, expiresInSeconds = 900) => ({
  accessToken,
  tokenType: 'Bearer',
  expiresInSeconds,
  user: USER,
})

/** Reads whatever the axios request interceptor would read on the next request. */
function currentToken() {
  const registrations = setAuthTokenProvider.mock.calls
    .map(([provider]) => provider)
    .filter((provider) => typeof provider === 'function')
  const latest = registrations.at(-1)
  return latest ? latest() : null
}

/** The recovery function the provider handed to the axios interceptor. */
function registeredRecovery() {
  const registrations = setSessionRecovery.mock.calls
    .map(([recovery]) => recovery)
    .filter((recovery) => typeof recovery === 'function')
  return registrations.at(-1)
}

function Probe() {
  const { status, user, signIn, signOut } = useAuth()

  return (
    <div>
      <p data-testid="status">{status}</p>
      <p data-testid="user">{user ? user.fullName : 'nobody'}</p>
      <button type="button" onClick={() => signIn({ email: USER.email, password: 'example-passphrase-9' })}>
        Sign in
      </button>
      <button type="button" onClick={signOut}>
        Sign out
      </button>
    </div>
  )
}

const renderProvider = () => render(
  <AuthProvider>
    <Probe />
  </AuthProvider>,
)

describe('AuthProvider', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()
    window.sessionStorage.clear()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('starts in a loading state, so a signed-in reload is not mistaken for a sign-out', async () => {
    let settle
    refresh.mockReturnValue(new Promise((resolve) => { settle = resolve }))

    renderProvider()

    // The httpOnly cookie is the only evidence of a session, and reading it costs a
    // round trip. Until that answers, "signed out" is not yet a fact.
    expect(screen.getByTestId('status')).toHaveTextContent('loading')

    await act(async () => {
      settle(session(RESTORED_TOKEN))
    })

    expect(screen.getByTestId('status')).toHaveTextContent('authenticated')
    expect(screen.getByTestId('user')).toHaveTextContent('Casey Rivers')
  })

  it('reports anonymous when there is no session to restore', async () => {
    refresh.mockRejectedValue(Object.assign(new Error('Please sign in again.'), { code: 'SESSION_EXPIRED', status: 401 }))

    renderProvider()

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('anonymous'))
    expect(screen.getByTestId('user')).toHaveTextContent('nobody')
    expect(currentToken()).toBeNull()
  })

  it('hands the access token to the API client and never to storage', async () => {
    refresh.mockRejectedValue(new Error('no session'))
    login.mockResolvedValue(session(SIGNED_IN_TOKEN))

    renderProvider()
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('anonymous'))

    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'))
    expect(currentToken()).toBe(SIGNED_IN_TOKEN)

    // The rule this protects: a token in localStorage is readable by any injected
    // script and outlives the tab. Asserting on the value rather than on a key name
    // means renaming a key cannot quietly defeat the test.
    const persisted = [window.localStorage, window.sessionStorage]
      .flatMap((store) => Object.keys(store).map((key) => store.getItem(key)))
      .join(' ')
    expect(persisted).not.toContain(SIGNED_IN_TOKEN)
  })

  it('collapses concurrent renewals into a single request', async () => {
    // The property under test is not efficiency. Refresh tokens rotate, and the server
    // treats a second presentation of a spent token as theft and revokes the session —
    // so two parallel renewals would sign the person out rather than keep them in.
    let settle
    refresh.mockReturnValue(new Promise((resolve) => { settle = resolve }))

    renderProvider()
    const recover = registeredRecovery()
    expect(recover).toBeTypeOf('function')

    let outcomes
    await act(async () => {
      const attempts = Promise.all([recover(), recover(), recover()])
      settle(session(RENEWED_TOKEN))
      outcomes = await attempts
    })

    expect(outcomes).toEqual([true, true, true])
    // One for the page-load bootstrap, which the three recovery calls joined.
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(currentToken()).toBe(RENEWED_TOKEN)
  })

  it('renews early, so no request ever fails because a token aged out', async () => {
    vi.useFakeTimers()
    refresh.mockResolvedValueOnce(session(RESTORED_TOKEN, 900))
    refresh.mockResolvedValueOnce(session(RENEWED_TOKEN, 900))

    renderProvider()
    await act(async () => {})
    expect(currentToken()).toBe(RESTORED_TOKEN)

    // 14 minutes: inside the token's 15, and past the minute of margin the provider
    // keeps. Nothing has failed at this point — the renewal is not a reaction to a 401.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(14 * 60 * 1000)
    })

    expect(refresh).toHaveBeenCalledTimes(2)
    expect(currentToken()).toBe(RENEWED_TOKEN)
  })

  it('signs out locally and stops renewing', async () => {
    vi.useFakeTimers()
    refresh.mockResolvedValue(session(RESTORED_TOKEN, 900))
    logout.mockResolvedValue(undefined)

    renderProvider()
    await act(async () => {})
    expect(screen.getByTestId('status')).toHaveTextContent('authenticated')

    await act(async () => {
      screen.getByRole('button', { name: 'Sign out' }).click()
    })

    expect(screen.getByTestId('status')).toHaveTextContent('anonymous')
    expect(currentToken()).toBeNull()

    // The scheduled renewal has to be cancelled, not merely ignored. A timer that
    // still fires would present the cleared cookie and log a failure every quarter
    // hour for as long as the tab stayed open.
    refresh.mockClear()
    await act(async () => {
      await vi.advanceTimersByTimeAsync(30 * 60 * 1000)
    })
    expect(refresh).not.toHaveBeenCalled()
  })

  it('makes one request on load even though StrictMode mounts twice', async () => {
    refresh.mockResolvedValue(session(RESTORED_TOKEN))

    await act(async () => {
      render(
        <StrictMode>
          <AuthProvider>
            <Probe />
          </AuthProvider>
        </StrictMode>,
      )
    })

    // Development's double mount is not a special case to tolerate here: presenting
    // the same refresh cookie twice is the exact thing reuse detection kills, so the
    // in-flight promise has to survive an unmount and remount.
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(screen.getByTestId('status')).toHaveTextContent('authenticated')
  })
})
