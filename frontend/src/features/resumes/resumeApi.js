import { apiClient } from '../../lib/apiClient.js'

/**
 * The resume endpoints, named.
 *
 * Two things here are less obvious than they look.
 *
 * **The upload sends `FormData` with no `Content-Type` header.** Setting it by hand is
 * the classic multipart bug: the boundary is generated with the body, so a hardcoded
 * `multipart/form-data` header arrives without one and the server finds no file. The
 * axios instance deliberately has no default JSON header for the same reason.
 *
 * **`onProgress` exists because a 5 MB upload is slow enough to look broken.** It
 * reports bytes sent, which is honest — it is not a percentage of the analysis, and
 * the UI labels it as the upload only.
 */

export async function listResumes() {
  const { data } = await apiClient.get('/resumes')
  return data
}

export async function getResume(id) {
  const { data } = await apiClient.get(`/resumes/${id}`)
  return data
}

export async function uploadResume({ file, label, onProgress }) {
  const body = new FormData()
  body.append('file', file)
  if (label) {
    body.append('label', label)
  }

  const { data } = await apiClient.post('/resumes/upload', body, {
    onUploadProgress: (event) => {
      if (!onProgress || !event.total) return
      onProgress(Math.round((event.loaded / event.total) * 100))
    },
  })
  return data
}

export async function deleteResume(id) {
  await apiClient.delete(`/resumes/${id}`)
}
