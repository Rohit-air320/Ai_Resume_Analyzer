import { useState } from 'react'
import { FileText, Plus } from 'lucide-react'
import ErrorState from '../../components/state/ErrorState.jsx'
import { SkeletonList } from '../../components/state/Skeleton.jsx'
import { count, formatRelative } from '../../lib/format.js'
import { useResource } from '../../lib/useResource.js'
import UploadResumeForm from './UploadResumeForm.jsx'
import { listResumes } from './resumeApi.js'

/**
 * Pick a resume, or add one without leaving the flow.
 *
 * The library comes first and the upload form is behind a toggle, which is the right way
 * round for the second visit onwards: the real loop of this product is one resume against
 * many postings, so re-uploading the same PDF every time would be the common case made
 * expensive.
 *
 * **Radios, not clickable cards.** This is a single choice from a set, which is what a
 * radio group is; using it means arrow keys work, the selection is announced, and the
 * form can be submitted from the keyboard without any of that being re-implemented.
 *
 * A resume whose text could not be extracted is shown but not selectable, with the
 * server's reason next to it. Hiding it would leave the person wondering where their
 * upload went; letting it be picked would score a resume against an empty document.
 */
export default function ResumePicker({ selectedId, onSelect }) {
  const [adding, setAdding] = useState(false)
  const resumes = useResource(() => listResumes(), [])

  if (resumes.isLoading) return <SkeletonList rows={2} label="Loading your resumes" />
  if (resumes.hasFailed) {
    return <ErrorState title="We could not load your resumes" error={resumes.error} onRetry={resumes.reload} />
  }

  const items = resumes.data ?? []

  function added(resume) {
    resumes.setData([resume, ...items])
    setAdding(false)
    if (resume.analysable) onSelect(resume)
  }

  return (
    <div className="space-y-4">
      {items.length > 0 ? (
        <ul className="space-y-2.5">
          {items.map((resume) => {
            const chosen = resume.id === selectedId

            return (
              <li key={resume.id}>
                <label
                  className={[
                    'flex items-start gap-3 rounded-card border px-4 py-3.5 transition-colors duration-150',
                    resume.analysable ? 'cursor-pointer' : 'cursor-not-allowed opacity-70',
                    chosen ? 'border-brand-500 bg-brand-600/5' : 'border-line hover:border-line-strong',
                  ].join(' ')}
                >
                  <input
                    type="radio"
                    name="resume"
                    className="mt-1 h-4 w-4 accent-brand-600"
                    checked={chosen}
                    disabled={!resume.analysable}
                    onChange={() => onSelect(resume)}
                  />

                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium text-ink">
                      {resume.label || resume.originalFilename}
                    </span>
                    <span className="mt-1 block text-xs text-ink-subtle">
                      {resume.analysable
                        ? `${count(resume.wordCount, 'word')} · ${count(resume.pageCount, 'page')} · added ${formatRelative(resume.createdAt)}`
                        : resume.extractionError || 'We could not read any text from this file.'}
                    </span>
                  </span>

                  <FileText size={16} className="mt-0.5 shrink-0 text-ink-subtle" aria-hidden="true" />
                </label>
              </li>
            )
          })}
        </ul>
      ) : (
        <p className="text-sm text-ink-muted">
          No resumes yet. Upload one below — a PDF or DOCX, up to 5 MB.
        </p>
      )}

      {adding || items.length === 0 ? (
        <div className="card p-5">
          <p className="eyebrow">Upload a resume</p>
          <div className="mt-4">
            <UploadResumeForm onUploaded={added} />
          </div>
        </div>
      ) : (
        <button type="button" onClick={() => setAdding(true)} className="btn btn-secondary">
          <Plus size={15} aria-hidden="true" />
          Upload another resume
        </button>
      )}
    </div>
  )
}
