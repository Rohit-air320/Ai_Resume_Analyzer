import { apiClient } from '../../lib/apiClient.js'

/**
 * One request that fills the dashboard: counts, score summary, trend, recent analyses,
 * the most frequently missed skills and the target role.
 *
 * Shaped like a screen rather than like a resource, which is a deliberate exception to
 * how the rest of this API is organised. Five REST-pure requests to paint the page a
 * user sees first and most often is a purity nobody logging in is served by.
 *
 * The three fields in `scores` are absent rather than zero on a new account — an
 * average over no analyses is not 0, and rendering it as 0 would greet a first-time
 * user with a verdict on a resume nobody has read yet.
 */
export async function fetchDashboard() {
  const { data } = await apiClient.get('/dashboard')
  return data
}
