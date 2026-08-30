import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import Settings from '../Settings.jsx'
import { AuthContext } from '../../features/auth/authContext.js'
import { ThemeProvider } from '../../features/theme/ThemeProvider.jsx'
import { THEME_PREFERENCES } from '../../features/theme/themeContext.js'

/**
 * Settings.
 *
 * This page is rendered inside the real `ThemeProvider` rather than a stubbed context,
 * because the thing worth testing is not that a radio changes a variable — it is that
 * choosing a theme reaches the two places a theme actually lives: the class on `<html>`
 * that every colour token hangs off, and the `localStorage` key the inline script in
 * `index.html` reads before first paint. Phase 11 exists partly because those two had
 * drifted apart, so they are asserted together here.
 *
 * `matchMedia` is replaced per test rather than once in setup. Both preferences this page
 * touches are media queries, and a test that cannot say "the operating system asks for
 * dark" cannot check the option that follows it.
 */

function mockMatchMedia({ dark = false, reduceMotion = false } = {}) {
  window.matchMedia = (query) => ({
    matches: query.includes('prefers-color-scheme: dark')
      ? dark
      : query.includes('prefers-reduced-motion')
        ? reduceMotion
        : false,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  })
}

function renderSettings({ signOut = vi.fn() } = {}) {
  const session = {
    status: 'authenticated',
    user: { email: 'casey@example.test', fullName: 'Casey Rivera' },
    isAuthenticated: true,
    isLoading: false,
    signIn: vi.fn(),
    signUp: vi.fn(),
    signOut,
    applyUser: vi.fn(),
  }

  render(
    <MemoryRouter>
      <AuthContext.Provider value={session}>
        <ThemeProvider>
          <Settings />
        </ThemeProvider>
      </AuthContext.Provider>
    </MemoryRouter>,
  )

  return { signOut }
}

const isDark = () => document.documentElement.classList.contains('dark')

describe('Settings', () => {
  const realMatchMedia = window.matchMedia

  beforeEach(() => {
    window.localStorage.clear()
    document.documentElement.classList.remove('dark')
    mockMatchMedia()
  })

  afterEach(() => {
    window.matchMedia = realMatchMedia
    vi.restoreAllMocks()
  })

  it('offers one radio per preference, and follows the system by default', () => {
    renderSettings()

    const themes = within(screen.getByRole('group', { name: 'Theme' }))
    const radios = themes.getAllByRole('radio')

    expect(radios).toHaveLength(THEME_PREFERENCES.length)
    expect(radios.map((radio) => radio.value)).toEqual(THEME_PREFERENCES)
    expect(themes.getByRole('radio', { name: /Follow my system/ })).toBeChecked()
  })

  it('writes the chosen theme where the pre-paint script will find it', async () => {
    const user = userEvent.setup()
    renderSettings()

    await user.click(screen.getByRole('radio', { name: /^Dark/ }))

    expect(isDark()).toBe(true)
    expect(window.localStorage.getItem('resumeiq.theme')).toBe('dark')
    expect(screen.getByText(/Showing the/)).toHaveTextContent('Showing the dark theme now.')

    await user.click(screen.getByRole('radio', { name: /^Light/ }))

    expect(isDark()).toBe(false)
    expect(window.localStorage.getItem('resumeiq.theme')).toBe('light')
  })

  it('resolves "follow my system" against the operating system, not against light', async () => {
    mockMatchMedia({ dark: true })
    const user = userEvent.setup()
    renderSettings()

    // The OS says dark and nothing is stored, so that is what starts on screen.
    expect(isDark()).toBe(true)

    await user.click(screen.getByRole('radio', { name: /^Light/ }))
    expect(isDark()).toBe(false)

    // And back: the third state is the only route from an explicit choice to the OS one.
    await user.click(screen.getByRole('radio', { name: /Follow my system/ }))
    expect(isDark()).toBe(true)
    expect(window.localStorage.getItem('resumeiq.theme')).toBe('system')
  })

  it('reports the motion setting it detected and offers no switch for it', () => {
    mockMatchMedia({ reduceMotion: true })
    renderSettings()

    expect(screen.getByText(/asks for reduced motion, so animations are off/)).toBeInTheDocument()
    expect(screen.getByText(/no switch here on purpose/)).toBeInTheDocument()
    expect(screen.queryByRole('switch')).not.toBeInTheDocument()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
  })

  it('signs out through the session, not by clearing storage itself', async () => {
    const user = userEvent.setup()
    const { signOut } = renderSettings()

    await user.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(signOut).toHaveBeenCalledTimes(1)
  })

  it('has no control that does nothing', () => {
    renderSettings()

    // The page's whole claim is that it does not fake settings. Every interactive element
    // is either one of the three theme radios, the sign-out button, or a link to a page
    // that exists — so the full inventory is short enough to assert exactly.
    expect(screen.getAllByRole('radio')).toHaveLength(THEME_PREFERENCES.length)
    expect(
      screen.getAllByRole('button').map((button) => button.textContent.trim()),
    ).toEqual(['Sign out'])
    expect(screen.getAllByRole('link').map((link) => link.getAttribute('href'))).toEqual([
      '/resumes',
      '/analyses',
      '/profile',
      '/system-check',
    ])
    expect(screen.queryAllByRole('textbox')).toHaveLength(0)
  })

  it('says what is stored and that none of it is published', () => {
    renderSettings()

    const stored = within(screen.getByRole('region', { name: 'What is stored' }))

    expect(stored.getByText(/Your resume files and their text/)).toBeInTheDocument()
    expect(stored.getByText(/Nothing is published, and no resume text appears in a URL/)).toBeInTheDocument()
    expect(stored.getByText(/Deleting a resume deletes/)).toBeInTheDocument()
  })
})
