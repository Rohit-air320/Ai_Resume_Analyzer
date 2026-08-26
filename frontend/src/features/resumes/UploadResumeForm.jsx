import { useRef, useState } from 'react'
import { FileUp, Loader2, UploadCloud } from 'lucide-react'
import FormError from '../../components/form/FormError.jsx'
import TextField from '../../components/form/TextField.jsx'
import { formatBytes } from '../../lib/format.js'
import { describeFailure } from '../../lib/formErrors.js'
import { uploadResume } from './resumeApi.js'

/**
 * The upload form, used by both the resume library and step one of a new analysis.
 *
 * **The drop zone is a `<label>` wrapping a real file input.** Not a div with click and
 * drop handlers, which is the usual shortcut and the usual accessibility failure: a
 * label keeps the keyboard path, the focus ring, the screen-reader announcement and the
 * native file dialog for free, and drag-and-drop becomes an enhancement on top rather
 * than the only way in.
 *
 * **The client-side checks duplicate the server's on purpose.** The server is the
 * authority and rejects the same cases with the same limits; doing it here as well
 * turns a five-megabyte round trip and a 413 into an instant, specific sentence. The
 * numbers are the ones the API documents, and if they diverge the server still wins —
 * this can only be wrong in the direction of asking first.
 *
 * The uploaded resume is handed to the caller rather than kept here, because the two
 * callers do different things with it: the library adds it to a list, the wizard selects
 * it and moves on.
 */

const MAX_BYTES = 5 * 1024 * 1024
const ACCEPTED = ['.pdf', '.docx']

function localProblem(file) {
  if (!file) return 'Choose a PDF or DOCX file.'
  const name = file.name.toLowerCase()
  if (!ACCEPTED.some((extension) => name.endsWith(extension))) {
    return 'That file type is not supported. Upload a PDF or a DOCX.'
  }
  if (file.size > MAX_BYTES) {
    return `That file is ${formatBytes(file.size)}. The limit is 5 MB.`
  }
  if (file.size === 0) {
    return 'That file is empty.'
  }
  return null
}

export default function UploadResumeForm({ onUploaded }) {
  const [file, setFile] = useState(null)
  const [label, setLabel] = useState('')
  const [dragging, setDragging] = useState(false)
  const [progress, setProgress] = useState(0)
  const [pending, setPending] = useState(false)
  const [failure, setFailure] = useState({ message: null, fieldErrors: {} })
  const inputRef = useRef(null)

  function choose(candidate) {
    const problem = localProblem(candidate)
    setFailure({ message: problem, fieldErrors: {} })
    setFile(problem ? null : candidate)

    // A sensible default label from the filename, so the field is optional in practice.
    if (!problem && !label) {
      setLabel(candidate.name.replace(/\.(pdf|docx)$/i, '').slice(0, 120))
    }
  }

  async function submit(event) {
    event.preventDefault()
    const problem = localProblem(file)
    if (problem) {
      setFailure({ message: problem, fieldErrors: {} })
      return
    }

    setPending(true)
    setProgress(0)
    setFailure({ message: null, fieldErrors: {} })

    try {
      const resume = await uploadResume({ file, label: label.trim(), onProgress: setProgress })
      setFile(null)
      setLabel('')
      if (inputRef.current) inputRef.current.value = ''
      onUploaded?.(resume)
    } catch (error) {
      setFailure(describeFailure(error))
    } finally {
      setPending(false)
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <label
        onDragOver={(event) => {
          event.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(event) => {
          event.preventDefault()
          setDragging(false)
          choose(event.dataTransfer.files?.[0] ?? null)
        }}
        className={[
          'flex cursor-pointer flex-col items-center rounded-card border border-dashed px-6 py-9 text-center transition-colors duration-150',
          dragging ? 'border-brand-500 bg-brand-600/5' : 'border-line-strong bg-surface-sunken hover:border-brand-400',
        ].join(' ')}
      >
        <UploadCloud size={22} className="text-brand-600" aria-hidden="true" />
        <span className="mt-3 text-sm font-medium text-ink">
          {file ? file.name : 'Drop your resume here, or browse'}
        </span>
        <span className="mt-1 text-xs text-ink-subtle">
          {file ? formatBytes(file.size) : 'PDF or DOCX, up to 5 MB'}
        </span>

        <input
          ref={inputRef}
          type="file"
          name="file"
          accept=".pdf,.docx,application/pdf"
          className="sr-only"
          onChange={(event) => choose(event.target.files?.[0] ?? null)}
        />
      </label>

      <TextField
        label="Label"
        name="label"
        value={label}
        onChange={(event) => setLabel(event.target.value)}
        error={failure.fieldErrors.label}
        hint="What this version is for — “Backend CV”, “Data roles”. Optional."
        maxLength={120}
      />

      <FormError message={failure.message} />

      {pending && progress > 0 ? (
        <p className="text-xs text-ink-muted" aria-live="polite">
          Uploading… <span data-numeric="">{progress}</span>%
        </p>
      ) : null}

      <button type="submit" disabled={pending || !file} className="btn btn-primary w-full sm:w-auto">
        {pending
          ? <Loader2 size={16} className="animate-spin" aria-hidden="true" />
          : <FileUp size={16} aria-hidden="true" />}
        {pending ? 'Reading your resume…' : 'Upload resume'}
      </button>
    </form>
  )
}
