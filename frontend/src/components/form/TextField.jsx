import { useId } from 'react'

/**
 * One labelled text input, with the accessibility plumbing done once.
 *
 * Three things this exists to stop being retyped per field, and getting wrong on one
 * of them: the label is bound to the input by id, `aria-invalid` marks a field a
 * screen reader should announce as wrong, and `aria-describedby` points at whichever
 * of hint and error is actually on screen. Colour alone marking a field invalid is
 * the classic version of this bug — it says nothing to anybody not looking at it.
 *
 * `useId` rather than a caller-supplied id, so two of these on one page cannot
 * collide, which is exactly what happens the first time a form is reused inside a
 * modal.
 *
 * @param {object} props
 * @param {string} props.label       visible label text
 * @param {string} props.name        form field name, also the autofill hint
 * @param {string} [props.type]      input type, default text
 * @param {string} props.value       current value, controlled
 * @param {Function} props.onChange  receives the change event
 * @param {string} [props.error]     server or client validation message
 * @param {string} [props.hint]      quiet guidance shown while the field is valid
 * @param {string} [props.autoComplete] browser autofill token
 */
export default function TextField({
  label,
  name,
  type = 'text',
  value,
  onChange,
  error,
  hint,
  autoComplete,
  ...rest
}) {
  const id = useId()
  const errorId = `${id}-error`
  const hintId = `${id}-hint`
  const describedBy = error ? errorId : hint ? hintId : undefined

  return (
    <div>
      <label htmlFor={id} className="field-label">
        {label}
      </label>

      <input
        id={id}
        name={name}
        type={type}
        value={value}
        onChange={onChange}
        autoComplete={autoComplete}
        aria-invalid={error ? 'true' : undefined}
        aria-describedby={describedBy}
        className={`field mt-2 ${error ? 'field-invalid' : ''}`}
        {...rest}
      />

      {error ? (
        <p id={errorId} className="mt-2 text-xs text-danger-600">
          {error}
        </p>
      ) : hint ? (
        <p id={hintId} className="mt-2 text-xs text-ink-subtle">
          {hint}
        </p>
      ) : null}
    </div>
  )
}
