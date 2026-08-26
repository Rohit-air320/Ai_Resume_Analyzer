import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { setAuthTokenProvider, setSessionRecovery } from '../../lib/apiClient.js'
import { AuthContext } from './authContext.js'
import { login, logout, refresh, register } from './authApi.js'

/**
 * Owns the session for the whole app.
 *
 * Three decisions are worth defending here.
 *
 * **The access token is a ref, not state.** Nothing renders it, so putting it in
 * state would re-render the entire tree every time it rotated, and — worse — the
 * axios interceptor needs the current value synchronously, which a state variable
 * captured in a closure cannot promise. It is also never written to localStorage or
 * sessionStorage: anything script can read, injected script can read too, and a
 * token in storage outlives the tab that earned it. A refresh of the page costs one
 * silent call to /auth/refresh, which is a fair price.
 *
 * **Renewal is single-flight.** The refresh token rotates on every use and the
 * server treats a second presentation of a spent token as theft, revoking the whole
 * session. Two concurrent renewals would therefore not just waste a request — they
 * would sign the person out. The in-flight promise below is what makes three
 * simultaneous 401s produce one refresh call.
 *
 * **Renewal happens early, not on failure.** The token's lifetime arrives in the
 * response, so a timer renews it a minute before it dies. The 401 path still exists
 * because a laptop that slept through the window will find its token already gone,
 * but in normal use no request ever fails for age.
 */

const RENEWAL_MARGIN_SECONDS = 60
const MIN_RENEWAL_SECONDS = 10

const STATUS = {
  LOADING: 'loading',
  AUTHENTICATED: 'authenticated',
  ANONYMOUS: 'anonymous',
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState({ status: STATUS.LOADING, user: null })

  const accessToken = useRef(null)
  const renewalInFlight = useRef(null)
  const renewalTimer = useRef(null)
  const renewLater = useRef(null)

  const cancelRenewal = useCallback(() => {
    if (renewalTimer.current) {
      window.clearTimeout(renewalTimer.current)
      renewalTimer.current = null
    }
  }, [])

  const adopt = useCallback((issued) => {
    accessToken.current = issued.accessToken
    setSession({ status: STATUS.AUTHENTICATED, user: issued.user })

    cancelRenewal()
    const lifetime = Number(issued.expiresInSeconds) || 0
    const delaySeconds = Math.max(lifetime - RENEWAL_MARGIN_SECONDS, MIN_RENEWAL_SECONDS)
    renewalTimer.current = window.setTimeout(() => {
      renewLater.current?.()
    }, delaySeconds * 1000)

    return issued.user
  }, [cancelRenewal])

  const forget = useCallback(() => {
    accessToken.current = null
    cancelRenewal()
    setSession({ status: STATUS.ANONYMOUS, user: null })
  }, [cancelRenewal])

  /**
   * Renews the session, sharing one request among every caller.
   *
   * Resolves to true or false rather than throwing: both callers — the interceptor
   * deciding whether to retry, and the page-load bootstrap deciding what to render —
   * want an answer, not an exception. A rejection here means "not signed in", which
   * is the ordinary state of a first-time visitor.
   */
  const renew = useCallback(() => {
    if (!renewalInFlight.current) {
      renewalInFlight.current = refresh()
        .then((issued) => {
          adopt(issued)
          return true
        })
        .catch(() => {
          forget()
          return false
        })
        .finally(() => {
          renewalInFlight.current = null
        })
    }
    return renewalInFlight.current
  }, [adopt, forget])

  // Registered before anything else runs, so no request can be made with a token
  // this provider has not yet handed over. The cleanup matters in tests, where one
  // provider unmounting must not leave the shared axios instance pointing at it.
  useEffect(() => {
    renewLater.current = renew
    setAuthTokenProvider(() => accessToken.current)
    setSessionRecovery(renew)

    return () => {
      renewLater.current = null
      setAuthTokenProvider(null)
      setSessionRecovery(null)
    }
  }, [renew])

  // One silent renewal on load, to answer "is anybody signed in?" from the httpOnly
  // cookie. StrictMode invokes this twice in development; the in-flight promise
  // collapses both into a single request, which is not a nicety — a second refresh
  // with the same cookie is exactly what reuse detection revokes a session for.
  useEffect(() => {
    renew()
    return cancelRenewal
  }, [renew, cancelRenewal])

  const signIn = useCallback(async (credentials) => adopt(await login(credentials)), [adopt])

  const signUp = useCallback(async (details) => adopt(await register(details)), [adopt])

  const signOut = useCallback(async () => {
    // Local state is cleared whatever the server said. logout() already swallows its
    // own failures, and a person who clicked "sign out" must not be left looking at
    // their dashboard because a request timed out.
    await logout()
    forget()
  }, [forget])

  // The profile screen owns the only other place the user record changes. Handing it a
  // setter keeps one copy of the session: without this, renaming yourself would update
  // the form and leave the old name in the top bar until the next reload, which reads as
  // "the save did not work". It replaces the user only — never the token or the status.
  const applyUser = useCallback((user) => {
    setSession((current) => (current.user ? { ...current, user } : current))
  }, [])

  const value = useMemo(() => ({
    status: session.status,
    user: session.user,
    isAuthenticated: session.status === STATUS.AUTHENTICATED,
    isLoading: session.status === STATUS.LOADING,
    signIn,
    signUp,
    signOut,
    applyUser,
  }), [session, signIn, signUp, signOut, applyUser])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
