import { LONG_TIMEOUT_MS, apiClient } from '../../lib/apiClient.js'

/**
 * The analysis endpoints.
 *
 * `runAnalysis` is the one call in the app that carries its own timeout. Analysis is
 * synchronous by design — the server extracts text, scores it, and may wait on an AI
 * provider before answering — so the default 30 seconds is too tight while two minutes
 * is generous enough that a timeout means something is actually wrong. The alternative
 * design, a 202 and a status endpoint to poll, buys scalability this product does not
 * need and costs every client a state machine.
 *
 * `listAnalyses` returns summary rows only: scores, the job title, the resume label.
 * The skills, keywords and advice come with a single analysis, because a history table
 * that loaded them would fetch four child collections per row to render three numbers.
 */

export async function listAnalyses() {
  const { data } = await apiClient.get('/analyses')
  return data
}

export async function getAnalysis(id) {
  const { data } = await apiClient.get(`/analyses/${id}`)
  return data
}

export async function runAnalysis({ resumeId, jobDescriptionId }) {
  const { data } = await apiClient.post(
    '/analyses',
    { resumeId, jobDescriptionId },
    { timeout: LONG_TIMEOUT_MS },
  )
  return data
}

export async function deleteAnalysis(id) {
  await apiClient.delete(`/analyses/${id}`)
}
