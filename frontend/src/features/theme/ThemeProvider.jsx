import { useCallback, useEffect, useMemo, useState } from 'react'
import { ThemeContext } from './themeContext.js'

/**
 * Light or dark, for the whole app.
 *
 * The mechanism is one class on `<html>`, because every colour in this project is a
 * CSS custom property and `.dark` redefines the same token names. That is why no
 * component in the codebase mentions a colour twice, and why adding a screen costs
 * nothing in dark mode — the token it asks for already has two values.
 *
 * **Where the choice is kept.** `sessionStorage`, per the brief: remembered while the
 * tab is open, forgotten afterwards. That is a deliberately modest promise and it
 * avoids the flash-of-wrong-theme problem that `localStorage` creates without a
 * blocking inline script in `index.html`. First visit follows the operating system.
 *
 * Reads and writes are wrapped, because storage throws rather than returning null in
 * a locked-down browser, and a preference is never worth a blank page.
 */

const STORAGE_KEY = 'resumeiq.theme'

function readStoredTheme() {
  try {
    const stored = window.sessionStorage.getItem(STORAGE_KEY)
    return stored === 'light' || stored === 'dark' ? stored : null
  } catch {
    return null
  }
}

function systemTheme() {
  return window.matchMedia?.('(prefers-color-scheme: dark)')?.matches ? 'dark' : 'light'
}

export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(() => readStoredTheme() ?? systemTheme())

  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark')
    try {
      window.sessionStorage.setItem(STORAGE_KEY, theme)
    } catch {
      // A preference that cannot be saved still applies for this render.
    }
  }, [theme])

  const toggleTheme = useCallback(() => {
    setTheme((current) => (current === 'dark' ? 'light' : 'dark'))
  }, [])

  const value = useMemo(() => ({ theme, toggleTheme }), [theme, toggleTheme])

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}
