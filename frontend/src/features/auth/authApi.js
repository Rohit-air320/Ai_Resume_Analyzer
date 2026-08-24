import { apiClient } from '../../lib/apiClient.js'

/**
 * Every authentication call the app makes, in one place.
 *
 * Thin on purpose. These functions add no state and no decisions — they name the
 * endpoints and return what came back. The decisions (where the token lives, when
 * to renew it, what to do when renewal fails) belong to AuthContext, and keeping
 * them out of here is what makes both halves testable: a component test can mock
 * this module without reasoning about a session, and a session test can drive it
 * without a server.
 *
 * The refresh token is absent from every signature. It travels as an httpOnly
 * cookie the browser attaches by itself, which is precisely why no JavaScript in
 * this file could pass it even if it wanted to.
 */

/** @returns {Promise<{accessToken: string, expiresInSeconds: number, user: object}>} */
export async function register({ email, password, fullName }) {
  const { data } = await apiClient.post('/auth/register', { email, password, fullName })
  return data
}

export async function login({ email, password }) {
  const { data } = await apiClient.post('/auth/login', { email, password })
  return data
}

/**
 * Exchanges the refresh cookie for a new session.
 *
 * Called on page load — before the app knows whether anybody is signed in — and
 * again whenever an access token has aged out. A 401 here is the normal answer for
 * a visitor who has never signed in, so callers treat the rejection as "anonymous"
 * rather than as an error worth showing.
 */
export async function refresh() {
  const { data } = await apiClient.post('/auth/refresh')
  return data
}

/**
 * Ends the session on the server and clears the cookie.
 *
 * Always resolves. A sign-out that fails because the network is down must still
 * sign the person out of this tab: leaving them looking at a dashboard they asked
 * to leave is worse than an orphaned row, and the row expires on its own.
 */
export async function logout() {
  try {
    await apiClient.post('/auth/logout')
  } catch {
    // Deliberately swallowed — see above.
  }
}

export async function fetchCurrentUser() {
  const { data } = await apiClient.get('/auth/me')
  return data
}
