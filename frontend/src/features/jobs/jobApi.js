import { apiClient } from '../../lib/apiClient.js'

/**
 * The job description endpoints.
 *
 * `savePosting` sends the same posting text as often as the user pastes it, and that
 * is intentional: the server recognises a duplicate by content hash and returns the
 * saved one instead of creating a second row. Deduplicating in the browser would need
 * the same hash implemented twice, in two languages, agreeing forever.
 */

export async function listPostings() {
  const { data } = await apiClient.get('/job-descriptions')
  return data
}

export async function getPosting(id) {
  const { data } = await apiClient.get(`/job-descriptions/${id}`)
  return data
}

export async function savePosting({ title, company, text }) {
  const { data } = await apiClient.post('/job-descriptions', { title, company, text })
  return data
}

export async function deletePosting(id) {
  await apiClient.delete(`/job-descriptions/${id}`)
}
