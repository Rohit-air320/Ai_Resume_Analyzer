import { createContext, useContext } from 'react'

/**
 * The theme context and its hook, split from the provider for the same reason as
 * {@link ../auth/authContext.js}: a module that exports both a component and a hook
 * loses Vite's fast refresh, and every edit would reset the app's state.
 *
 * `theme` is the resolved answer — what is on screen — and `preference` is what the
 * person asked for, which may be `system`. A component that renders a colour wants the
 * first; only the settings page wants the second.
 *
 * @typedef {object} ThemeState
 * @property {'light'|'dark'} theme
 * @property {'system'|'light'|'dark'} preference
 * @property {(next: 'system'|'light'|'dark') => void} setPreference
 * @property {() => void} toggleTheme
 */
export const ThemeContext = createContext(null)

/**
 * The three values the preference may take, in the order the settings page lists them.
 * It lives here rather than in the provider because a module that exports a component
 * and a constant loses fast refresh — the same reason the context itself is split out.
 */
export const THEME_PREFERENCES = ['system', 'light', 'dark']

/**
 * Reads the theme. Returns a working default outside a provider rather than throwing,
 * because unlike the session, a missing theme is not a bug worth a blank screen — a
 * component test that renders a button in isolation should not need a provider.
 */
export const useTheme = () => useContext(ThemeContext) ?? {
  theme: 'light',
  preference: 'system',
  setPreference: () => {},
  toggleTheme: () => {},
}
