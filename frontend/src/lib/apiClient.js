import axios from 'axios'

/**
 * Single axios instance for the whole app.
 *
 * Two jobs beyond plain HTTP:
 *  1. attach the JWT, without this module needing to know how auth stores it
 *  2. normalise every failure into one ApiError shape, so components never touch
 *     error.response?.data?.something and never see a raw axios error
 */

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

/** Analysis calls wait on an AI provider, so they pass their own longer timeout. */
export const DEFAULT_TIMEOUT_MS = 30_000
export const LONG_TIMEOUT_MS = 120_000

export const apiClient = axios.create({
  baseURL,
  timeout: DEFAULT_TIMEOUT_MS,
  // No default Content-Type on purpose. axios already sends application/json for
  // plain objects, and a hardcoded JSON header would make it serialise the resume
  // upload's FormData to JSON instead of multipart, dropping the file.
})

/**
 * Auth registers a getter here in Phase 3, which keeps this module free of any
 * dependency on React context or storage decisions.
 */
let readAuthToken = () => null

export function setAuthTokenProvider(provider) {
  readAuthToken = typeof provider === 'function' ? provider : () => null
}

apiClient.interceptors.request.use((config) => {
  const token = readAuthToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** Mirrors the backend's ApiErrorResponse, plus the two failures that never reach it. */
export class ApiError extends Error {
  constructor({ code, message, fieldErrors = [], status = 0 }) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.fieldErrors = fieldErrors
  }

  /** Turns fieldErrors into { email: 'must be a valid email address' } for forms. */
  fieldErrorMap() {
    return this.fieldErrors.reduce((accumulator, violation) => {
      accumulator[violation.field] = violation.message
      return accumulator
    }, {})
  }
}

const FALLBACK_MESSAGES = {
  400: 'Please check the details you entered.',
  401: 'Your session has expired. Please sign in again.',
  403: 'You do not have access to that.',
  404: 'We could not find what you were looking for.',
  409: 'That conflicts with something that already exists.',
  413: 'That file is too large.',
  415: 'That file type is not supported.',
  422: 'We could not read that file. Try exporting it again as a PDF.',
  429: 'Too many requests. Please wait a moment and try again.',
  500: 'Something went wrong on our side. Please try again.',
  502: 'The analysis service returned an unexpected response. Please try again.',
  503: 'The analysis service is busy right now. Please try again in a moment.',
}

export function toApiError(error) {
  if (error?.code === 'ECONNABORTED' || error?.code === 'ETIMEDOUT') {
    return new ApiError({
      code: 'TIMEOUT',
      message: 'That took longer than expected. Please try again.',
      status: 0,
    })
  }

  if (!error?.response) {
    return new ApiError({
      code: 'NETWORK_ERROR',
      message: 'Cannot reach the server. Check your connection and that the API is running.',
      status: 0,
    })
  }

  const { status, data } = error.response
  return new ApiError({
    code: data?.code || 'INTERNAL_ERROR',
    message: data?.message || FALLBACK_MESSAGES[status] || 'Something went wrong. Please try again.',
    fieldErrors: Array.isArray(data?.fieldErrors) ? data.fieldErrors : [],
    status,
  })
}

apiClient.interceptors.response.use(
  (response) => response,
  (error) => Promise.reject(toApiError(error)),
)
