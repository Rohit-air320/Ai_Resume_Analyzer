import { useCallback, useEffect, useMemo, useState } from 'react'
import { THEME_PREFERENCES, ThemeContext } from './themeContext.js'

/**
 * Light or dark, for the whole app.
 *
 * The mechanism is one class on `<html>`, because every colour in this project is a
 * CSS custom property and `.dark` redefines the same token names. That is why no
 * component in the codebase mentions a colour twice, and why adding a screen costs
 * nothing in dark mode — the token it asks for already has two values.
 *
 * **Three states, not two.** The stored value is a *preference* — `system`, `light` or
 * `dark` — and the resolved `theme` is what that preference means right now. Without the
 * third state there is no way back to "whatever my laptop is doing": the first tap of a
 * two-way switch would permanently pin the app to a theme, and an operating system that
 * turns dark at sunset would stop being followed. `system` is the default, and while it
 * is selected the media query is *listened to*, not merely read once at startup.
 *
 * **Where the choice is kept.** `localStorage`, under the same key the inline script in
 * `index.html` reads before first paint. Phase 11 changed this: the provider had been
 * writing `sessionStorage` while that script read `localStorage`, so the two never met —
 * a returning visitor got a correct first paint from the OS and then, one render later,
 * whatever the tab happened to remember. Two mechanisms for one decision is one too many;
 * the inline script is the one that cannot flash, so the provider stores where it looks.
 *
 * Reads and writes are wrapped, because storage throws rather than returning null in a
 * locked-down browser, and a preference is never worth a blank page.
 */

const STORAGE_KEY = 'resumeiq.theme'
const DARK_QUERY = '(prefers-color-scheme: dark)'

function readStoredPreference() {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY)
    return THEME_PREFERENCES.includes(stored) ? stored : 'system'
  } catch {
    return 'system'
  }
}

function systemPrefersDark() {
  return Boolean(window.matchMedia?.(DARK_QUERY)?.matches)
}

export function ThemeProvider({ children }) {
  const [preference, setPreference] = useState(readStoredPreference)
  const [systemDark, setSystemDark] = useState(systemPrefersDark)

  const theme = preference === 'system' ? (systemDark ? 'dark' : 'light') : preference

  // The OS can change while the tab is open — a scheduled dark mode, or somebody
  // flipping it in system settings. `addEventListener` is the current API and
  // `addListener` the deprecated one Safari kept until 14; both are optional here
  // because jsdom's matchMedia has neither in some versions.
  useEffect(() => {
    const media = window.matchMedia?.(DARK_QUERY)
    if (!media) return undefined

    const onChange = (event) => setSystemDark(event.matches)

    media.addEventListener?.('change', onChange)
    if (!media.addEventListener) media.addListener?.(onChange)

    return () => {
      media.removeEventListener?.('change', onChange)
      if (!media.removeEventListener) media.removeListener?.(onChange)
    }
  }, [])

  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark')
  }, [theme])

  useEffect(() => {
    try {
      window.localStorage.setItem(STORAGE_KEY, preference)
    } catch {
      // A preference that cannot be saved still applies for this session.
    }
  }, [preference])

  const choosePreference = useCallback((next) => {
    setPreference(THEME_PREFERENCES.includes(next) ? next : 'system')
  }, [])

  // The top bar's switch is still one button, and it commits to a side: from `system`
  // it takes whichever theme is not showing, which is the only reading of a click on a
  // control that says "switch to dark".
  const toggleTheme = useCallback(() => {
    setPreference(theme === 'dark' ? 'light' : 'dark')
  }, [theme])

  const value = useMemo(
    () => ({ theme, preference, setPreference: choosePreference, toggleTheme }),
    [theme, preference, choosePreference, toggleTheme],
  )

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}
