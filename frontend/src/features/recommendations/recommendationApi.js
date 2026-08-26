import { apiClient } from '../../lib/apiClient.js'

/**
 * Every piece of advice the account has ever been given, newest first.
 *
 * Recommendations are produced per analysis, but they are read across analyses — "what
 * should I learn next" is not a question about one job posting. So this endpoint flattens
 * them, carries the job title each one came from, and links back to the analysis that
 * produced it, which is what keeps the flat list honest: no advice appears without the
 * comparison that justified it.
 *
 * `type` is one of IMPROVEMENT, LEARNING, PROJECT or KEYWORD. Omitting it returns all
 * four. An unrecognised value is a 400 from the server rather than a silently empty list,
 * so a typo in a caller shows up as an error instead of as "no recommendations yet".
 *
 * The response is a bare array, capped server-side at 100.
 *
 * @param {string} [type]
 */
export async function listRecommendations(type) {
  const { data } = await apiClient.get('/recommendations', {
    params: type ? { type } : undefined,
  })
  return data
}
