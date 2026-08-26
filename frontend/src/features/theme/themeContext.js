import { createContext, useContext } from 'react'

/**
 * The theme context and its hook, split from the provider for the same reason as
 * {@link ../auth/authContext.js}: a module that exports both a component and a hook
 * loses Vite's fast refresh, and every edit would reset the app's state.
 *
 * @typedef {object} ThemeState
 * @property {'light'|'dark'} theme
 * @property {() => void} toggleTheme
 */
export const ThemeContext = createContext(null)

/**
 * Reads the theme. Returns a working default outside a provider rather than throwing,
 * because unlike the session, a missing theme is not a bug worth a blank screen — a
 * component test that renders a button in isolation should not need a provider.
 */
export const useTheme = () => useContext(ThemeContext) ?? {
  theme: 'light',
  toggleTheme: () => {},
}
