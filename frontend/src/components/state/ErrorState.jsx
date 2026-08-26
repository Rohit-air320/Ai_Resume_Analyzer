import { AlertTriangle, RefreshCw } from 'lucide-react'

/**
 * A failed load, with the one action that can fix it.
 *
 * The message comes from the API's error envelope, which is written for the person
 * reading it — "We could not read any text from this resume" rather than a status
 * code. Rendering the code instead would be honest and useless.
 *
 * A retry button appears only when a retry could plausibly help, which the caller
 * decides by passing `onRetry`. Offering to retry a 404 teaches people that the button
 * does nothing.
 */
export default function ErrorState({ title = 'That did not load', error, onRetry }) {
  return (
    <div role="alert" className="panel p-6 sm:p-8">
      <AlertTriangle size={20} className="text-danger-500" aria-hidden="true" />
      <h2 className="mt-4 text-base font-semibold">{title}</h2>
      <p className="mt-2 max-w-prose text-sm text-ink-muted">
        {error?.message || 'Something went wrong. Please try again.'}
      </p>

      {onRetry ? (
        <button type="button" onClick={onRetry} className="btn btn-secondary mt-5">
          <RefreshCw size={15} aria-hidden="true" />
          Try again
        </button>
      ) : null}
    </div>
  )
}
