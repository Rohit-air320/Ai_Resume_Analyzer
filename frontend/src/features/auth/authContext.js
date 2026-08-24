import { createContext, useContext } from 'react'

/**
 * The auth context object and the hook that reads it, kept apart from the provider
 * component on purpose.
 *
 * A file that exports both a component and a hook breaks Vite's fast refresh — the
 * module gets a full reload instead of a hot swap, and every edit to the provider
 * would throw away the session it is holding. Splitting the two also means a test
 * can render a component against a hand-built context value without importing the
 * provider and the network calls behind it.
 */

/**
 * @typedef {object} AuthState
 * @property {'loading'|'authenticated'|'anonymous'} status
 * @property {object|null} user
 * @property {boolean} isAuthenticated
 * @property {boolean} isLoading
 * @property {(credentials: {email: string, password: string}) => Promise<object>} signIn
 * @property {(details: {email: string, password: string, fullName: string}) => Promise<object>} signUp
 * @property {() => Promise<void>} signOut
 */

export const AuthContext = createContext(null)

/**
 * Reads the session. Throws outside a provider rather than returning a null-ish
 * default, because a component that silently believes nobody is signed in is a
 * much harder bug to find than one that fails on first render.
 */
export const useAuth = () => {
  const value = useContext(AuthContext)
  if (!value) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return value
}
