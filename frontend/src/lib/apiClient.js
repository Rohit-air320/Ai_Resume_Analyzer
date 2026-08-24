import axios from 'axios'

/**
 * Single axios instance for the whole app.
 *
 * Three jobs beyond plain HTTP:
 *  1. attach the JWT, without this module needing to know how auth stores it
 *  2. normalise every failure into one ApiError shape, so components never touch
 *     error.response?.data?.something and never see a raw axios error
 *  3. renew a session once, silently, when a request arrives with a dead access
 *     token — so a 15-minute token is invisible to every calling component
 *
 * All three are registrations, not imports: auth hands this module a token getter
 * and a recovery function, and this module knows nothing about React context or
 * where a token is kept. That direction matters, because the alternative is a
 * cycle — auth calls the API, and the API would call auth.
 */

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

/** Analysis calls wait on an AI provider, so they pass their own longer timeout. */
export const DEFAULT_TIMEOUT_MS = 30_000
export const LONG_TIMEOUT_MS = 120_000

export const apiClient = axios.create({
  baseURL,
  timeout: DEFAULT_TIMEOUT_MS,
  // The refresh token is an httpOnly cookie, and axios omits cookies unless asked.
  // Without this, /api/auth/refresh arrives with nothing to rotate and every
  // session would end after fifteen minutes.
  withCredentials: true,
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

/**
 * Paths that must never trigger a recovery attempt.
 *
 * Sign-in and registration answer 401 when the credentials are wrong, and refresh
 * answers 401 when the session is over. Retrying any of those after a refresh is
 * either pointless or a loop: refresh failing would call refresh again.
 */
const NEVER_RECOVERED = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout']

/** Replaced by auth in Phase 3. Returns true when the retry is worth making. */
let recoverSession = async () => false

export function setSessionRecovery(recovery) {
  recoverSession = typeof recovery === 'function' ? recovery : async () => false
}

function isRecoverable(apiError, request) {
  // UNAUTHORIZED only. SESSION_EXPIRED means the refresh token itself is finished and
  // INVALID_CREDENTIALS means the password was wrong — neither improves on a second try.
  return apiError.status === 401
    && apiError.code === 'UNAUTHORIZED'
    && Boolean(request)
    && !request.sessionAlreadyRecovered
    && !NEVER_RECOVERED.some((path) => (request.url || '').startsWith(path))
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const apiError = toApiError(error)
    const request = error?.config

    if (!isRecoverable(apiError, request)) {
      return Promise.reject(apiError)
    }

    // Marked on the config, so one request gets one retry. A flag on this module
    // instead would let two concurrent requests each consume the other's attempt.
    request.sessionAlreadyRecovered = true

    if (!(await recoverSession())) {
      return Promise.reject(apiError)
    }

    // Re-entering through the instance rather than a bare axios call, so the request
    // interceptor runs again and picks up the token the refresh just produced.
    return apiClient.request(request)
  },
)
