/**
 * Turns a rejected API call into the two things a form needs to render.
 *
 * The backend distinguishes a field problem from a whole-request problem — a 400
 * carries `fieldErrors`, a 401 or 429 carries only a message — and a form has two
 * places to put them. Doing the split here means neither page repeats the reasoning,
 * and neither page reaches into `error.response.data`, which is the shape this
 * project deliberately never lets past the axios interceptor.
 *
 * Defensive about the error's own shape on purpose: a thrown TypeError from a bug in
 * a submit handler must still leave the person with a usable form and a message,
 * rather than an empty screen from a render that crashed while reporting a crash.
 */
export function describeFailure(error) {
  const fieldErrors = typeof error?.fieldErrorMap === 'function' ? error.fieldErrorMap() : {}

  return {
    message: error?.message || 'Something went wrong. Please try again.',
    fieldErrors,
  }
}

export const NO_FAILURE = { message: null, fieldErrors: {} }
