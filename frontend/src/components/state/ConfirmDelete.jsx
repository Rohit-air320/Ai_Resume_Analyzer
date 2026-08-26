import { useEffect, useRef, useState } from 'react'
import { Loader2, Trash2 } from 'lucide-react'

/**
 * Delete, with the confirmation inline instead of in a dialog.
 *
 * The button turns into a question in place: "Delete this?" with Cancel and Delete.
 * A modal would be the reflex, and it is the wrong tool here — it steals focus, needs
 * its own escape handling and focus trap, and covers the very row the person is trying
 * to identify. Confirming next to the thing being deleted keeps the context visible.
 *
 * Three details that make it safe rather than merely nice. The confirmation resets
 * itself after a few seconds, so a half-pressed delete does not sit armed on the page.
 * The pending state disables the control, because a double click on a delete is a
 * second request against a row that is already gone. And `aria-live` announces the
 * question, since a control that silently changes meaning is a trap for anybody not
 * watching it.
 *
 * @param {object} props
 * @param {() => Promise<void>} props.onConfirm the deletion itself
 * @param {string} props.label      accessible name, e.g. "Delete Backend CV"
 * @param {string} [props.question] the confirmation prompt
 */
export default function ConfirmDelete({ onConfirm, label, question = 'Delete this?' }) {
  const [armed, setArmed] = useState(false)
  const [pending, setPending] = useState(false)
  const disarmTimer = useRef(null)

  useEffect(() => () => window.clearTimeout(disarmTimer.current), [])

  function arm() {
    setArmed(true)
    disarmTimer.current = window.setTimeout(() => setArmed(false), 6000)
  }

  async function confirm() {
    window.clearTimeout(disarmTimer.current)
    setPending(true)
    try {
      await onConfirm()
    } finally {
      // The row usually unmounts on success, so this only runs when the delete failed
      // — and then the button must come back rather than stay spinning forever.
      setPending(false)
      setArmed(false)
    }
  }

  if (!armed) {
    return (
      <button
        type="button"
        onClick={arm}
        aria-label={label}
        className="btn btn-ghost px-2.5 py-2 text-ink-subtle hover:text-danger-600"
      >
        <Trash2 size={16} aria-hidden="true" />
      </button>
    )
  }

  return (
    <span className="flex items-center gap-1.5" aria-live="polite">
      <span className="text-xs text-ink-muted">{question}</span>
      <button
        type="button"
        onClick={() => setArmed(false)}
        disabled={pending}
        className="btn btn-ghost px-2 py-1.5 text-xs"
      >
        Cancel
      </button>
      <button
        type="button"
        onClick={confirm}
        disabled={pending}
        className="btn px-2.5 py-1.5 text-xs font-semibold text-danger-600 hover:bg-danger-500/10"
      >
        {pending ? <Loader2 size={13} className="animate-spin" aria-hidden="true" /> : null}
        Delete
      </button>
    </span>
  )
}
