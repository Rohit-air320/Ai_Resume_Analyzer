import { AlertCircle } from 'lucide-react'

/**
 * The one thing that went wrong with a whole form, as opposed to with one field.
 *
 * `role="alert"` so a screen reader announces a failed sign-in without the person
 * having to go looking for it — a message that only appears visually is a message
 * somebody keeps pressing the button to find.
 *
 * Renders nothing when there is no message, so callers do not need their own guard.
 *
 * @param {object} props
 * @param {string|null} [props.message] text from the API's error envelope
 */
export default function FormError({ message }) {
  if (!message) {
    return null
  }

  return (
    <p
      role="alert"
      className="flex items-start gap-2.5 rounded-lg border border-danger-500/30 bg-danger-500/10 px-3.5 py-3 text-sm text-danger-600"
    >
      <AlertCircle size={16} className="mt-0.5 shrink-0" aria-hidden="true" />
      {message}
    </p>
  )
}
