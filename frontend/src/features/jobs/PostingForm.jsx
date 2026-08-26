import { useState } from 'react'
import { Loader2, Save } from 'lucide-react'
import FormError from '../../components/form/FormError.jsx'
import TextField from '../../components/form/TextField.jsx'
import { describeFailure } from '../../lib/formErrors.js'
import { savePosting } from './jobApi.js'

/**
 * Paste a job description.
 *
 * A textarea rather than a URL field, deliberately. Fetching a posting from a link
 * would mean the server making outbound requests to arbitrary sites on a user's behalf,
 * which is a request-forgery surface and a scraper to maintain against every job board's
 * markup. Pasting is one extra action for the user and removes both.
 *
 * The character counter is not decoration: the server refuses anything under 200
 * characters, because scoring a resume against a job title produces a confident number
 * that means nothing. Saying so while the person types is kinder than saying it after
 * they submit, so the count turns amber until the posting is long enough to score.
 */
export default function PostingForm({ onSaved }) {
  const [form, setForm] = useState({ title: '', company: '', text: '' })
  const [pending, setPending] = useState(false)
  const [failure, setFailure] = useState({ message: null, fieldErrors: {} })

  const characters = form.text.trim().length
  const tooShort = characters > 0 && characters < 200

  function update(field) {
    return (event) => setForm((current) => ({ ...current, [field]: event.target.value }))
  }

  async function submit(event) {
    event.preventDefault()
    setPending(true)
    setFailure({ message: null, fieldErrors: {} })

    try {
      const posting = await savePosting({
        title: form.title.trim(),
        company: form.company.trim() || null,
        text: form.text,
      })
      setForm({ title: '', company: '', text: '' })
      onSaved?.(posting)
    } catch (error) {
      setFailure(describeFailure(error))
    } finally {
      setPending(false)
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          label="Job title"
          name="title"
          value={form.title}
          onChange={update('title')}
          error={failure.fieldErrors.title}
          maxLength={160}
          required
        />
        <TextField
          label="Company"
          name="company"
          value={form.company}
          onChange={update('company')}
          error={failure.fieldErrors.company}
          hint="Optional."
          maxLength={160}
        />
      </div>

      <div>
        <label htmlFor="posting-text" className="field-label">
          Job description
        </label>
        <textarea
          id="posting-text"
          name="text"
          value={form.text}
          onChange={update('text')}
          rows={12}
          required
          aria-invalid={failure.fieldErrors.text ? 'true' : undefined}
          aria-describedby="posting-text-count"
          className={`field mt-2 resize-y font-sans leading-relaxed ${failure.fieldErrors.text ? 'field-invalid' : ''}`}
          placeholder="Paste the whole posting — responsibilities, requirements, nice-to-haves. The more of it, the better the match."
        />

        <p
          id="posting-text-count"
          aria-live="polite"
          className={`mt-2 text-xs ${tooShort ? 'text-warning-600' : 'text-ink-subtle'}`}
        >
          {failure.fieldErrors.text ?? (
            <>
              <span data-numeric="">{characters}</span> characters
              {tooShort ? ' — needs at least 200 to be worth scoring' : ''}
            </>
          )}
        </p>
      </div>

      <FormError message={failure.message} />

      <button
        type="submit"
        disabled={pending || characters < 200 || !form.title.trim()}
        className="btn btn-primary w-full sm:w-auto"
      >
        {pending
          ? <Loader2 size={16} className="animate-spin" aria-hidden="true" />
          : <Save size={16} aria-hidden="true" />}
        {pending ? 'Reading the posting…' : 'Save job description'}
      </button>
    </form>
  )
}
