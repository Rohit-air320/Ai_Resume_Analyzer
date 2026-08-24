import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import Login from '../Login.jsx'
import { AuthContext } from '../../features/auth/authContext.js'
import { ApiError } from '../../lib/apiClient.js'

/**
 * The sign-in screen, driven through a hand-built session value.
 *
 * No network and no provider: the context is supplied directly, which is the payoff
 * for keeping the context object in its own module. These tests are about what the
 * form does with an answer — where it puts a field error, what it clears, where it
 * sends you — and mounting the real provider would only add a refresh call to mock.
 */

const EMAIL = 'casey@example.test'
/** Says "example" because the repo's secret scanner is right to flag anything else. */
const TYPED_PASSWORD = 'example-passphrase-9'

function renderLogin({ signIn = vi.fn(), isAuthenticated = false, isLoading = false, from } = {}) {
  const value = {
    status: isAuthenticated ? 'authenticated' : 'anonymous',
    user: null,
    isAuthenticated,
    isLoading,
    signIn,
    signUp: vi.fn(),
    signOut: vi.fn(),
  }

  const entry = from ? { pathname: '/login', state: { from: { pathname: from } } } : '/login'

  render(
    <MemoryRouter initialEntries={[entry]}>
      <AuthContext.Provider value={value}>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/dashboard" element={<h1>Dashboard</h1>} />
          <Route path="/analyses/7" element={<h1>Analysis 7</h1>} />
        </Routes>
      </AuthContext.Provider>
    </MemoryRouter>,
  )

  return { signIn }
}

const emailField = () => screen.getByLabelText('Email')
const passwordField = () => screen.getByLabelText('Password')
/** Matches both labels, because the button renames itself to "Signing in…" mid-flight. */
const submit = () => screen.getByRole('button', { name: /sign(ing)? in/i })

describe('Login', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('sends what was typed and lands on the dashboard', async () => {
    const { signIn } = renderLogin({ signIn: vi.fn().mockResolvedValue({ email: EMAIL }) })

    await userEvent.type(emailField(), EMAIL)
    await userEvent.type(passwordField(), TYPED_PASSWORD)
    await userEvent.click(submit())

    expect(signIn).toHaveBeenCalledWith({ email: EMAIL, password: TYPED_PASSWORD })
    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
  })

  it('returns you to the page you were trying to reach', async () => {
    // The route guard puts the blocked URL in location state. Signing in has to finish
    // that journey, or a bookmarked analysis always costs an extra click.
    renderLogin({ signIn: vi.fn().mockResolvedValue({ email: EMAIL }), from: '/analyses/7' })

    await userEvent.type(emailField(), EMAIL)
    await userEvent.type(passwordField(), TYPED_PASSWORD)
    await userEvent.click(submit())

    expect(await screen.findByRole('heading', { name: 'Analysis 7' })).toBeInTheDocument()
  })

  it("shows the server's wording for a wrong password and keeps the email", async () => {
    const failure = new ApiError({
      code: 'INVALID_CREDENTIALS',
      message: 'Email or password is incorrect.',
      status: 401,
    })
    renderLogin({ signIn: vi.fn().mockRejectedValue(failure) })

    await userEvent.type(emailField(), EMAIL)
    await userEvent.type(passwordField(), TYPED_PASSWORD)
    await userEvent.click(submit())

    // role="alert", so it is announced rather than only drawn.
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Email or password is incorrect.')
    // The message must not be improved upon locally: wording like "wrong password" or
    // "no account" would tell a stranger whether the address is registered, which is the
    // one thing the API refuses to say. Scope the check to the alert — the page footer
    // permanently reads "Try the demo — no account needed", which is about the demo, not
    // this email, and a document-wide query would match it and fail for the wrong reason.
    // `n.t` rather than `n't` so the straight and curly apostrophe are both caught.
    expect(alert.textContent).not.toMatch(
      /no account|wrong password|not registered|does(n.t| not) exist/i,
    )

    expect(emailField()).toHaveValue(EMAIL)
    expect(passwordField()).toHaveValue('')
  })

  it('attaches a validation message to the field it belongs to', async () => {
    const failure = new ApiError({
      code: 'VALIDATION_FAILED',
      message: 'Please check the details you entered.',
      status: 400,
      fieldErrors: [{ field: 'email', message: 'Enter a valid email address' }],
    })
    renderLogin({ signIn: vi.fn().mockRejectedValue(failure) })

    await userEvent.type(emailField(), 'not-an-email')
    await userEvent.type(passwordField(), TYPED_PASSWORD)
    await userEvent.click(submit())

    await waitFor(() => expect(emailField()).toHaveAttribute('aria-invalid', 'true'))
    // Bound by aria-describedby, not merely rendered nearby — a red border says
    // nothing to anybody who is not looking at it.
    expect(emailField()).toHaveAccessibleDescription('Enter a valid email address')
    expect(passwordField()).not.toHaveAttribute('aria-invalid')
  })

  it('passes on a lockout in the server\'s words rather than inventing advice', async () => {
    const failure = new ApiError({
      code: 'TOO_MANY_REQUESTS',
      message: 'Too many sign-in attempts. Try again in 15 minutes.',
      status: 429,
    })
    renderLogin({ signIn: vi.fn().mockRejectedValue(failure) })

    await userEvent.type(emailField(), EMAIL)
    await userEvent.type(passwordField(), TYPED_PASSWORD)
    await userEvent.click(submit())

    expect(await screen.findByRole('alert')).toHaveTextContent('Try again in 15 minutes.')
  })

  it('disables the button while the request is in flight', async () => {
    let release
    renderLogin({ signIn: vi.fn(() => new Promise((resolve) => { release = resolve })) })

    await userEvent.type(emailField(), EMAIL)
    await userEvent.type(passwordField(), TYPED_PASSWORD)
    await userEvent.click(submit())

    // A second click would be a second sign-in attempt, and five failed attempts lock
    // the account — so double submission is not merely wasteful here.
    expect(submit()).toBeDisabled()
    expect(submit()).toHaveTextContent('Signing in…')

    release({ email: EMAIL })
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Dashboard' })).toBeInTheDocument())
  })

  it('does not offer the sign-in form to someone already signed in', async () => {
    renderLogin({ isAuthenticated: true })

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument()
  })
})
