import { AlertTriangle, FileText, Sparkles } from 'lucide-react'
import { Link } from 'react-router-dom'
import PageHeader from '../components/layout/PageHeader.jsx'
import ConfirmDelete from '../components/state/ConfirmDelete.jsx'
import EmptyState from '../components/state/EmptyState.jsx'
import ErrorState from '../components/state/ErrorState.jsx'
import { SkeletonList } from '../components/state/Skeleton.jsx'
import UploadResumeForm from '../features/resumes/UploadResumeForm.jsx'
import { deleteResume, listResumes } from '../features/resumes/resumeApi.js'
import { count, formatBytes, formatRelative } from '../lib/format.js'
import { useResource } from '../lib/useResource.js'

/**
 * The resume library.
 *
 * Keeping several versions is the point rather than an edge case — a backend CV and a
 * data CV score differently against the same posting, and seeing that difference is
 * most of the value here. So the label is prominent and the filename is secondary.
 *
 * **Deletes update the list locally instead of refetching.** The server has already
 * confirmed the row is gone; asking it again to render the same outcome adds a request
 * and a visible flicker. The list refetches on the next visit anyway.
 *
 * A failed extraction stays in the list with the server's reason attached. That is the
 * one case where showing a broken row is better than hiding it: the person needs to know
 * their scanned PDF has no text layer, which is fixable, rather than assume the upload
 * silently failed.
 */
export default function Resumes() {
  const resumes = useResource(() => listResumes(), [])
  const items = resumes.data ?? []

  async function remove(resume) {
    await deleteResume(resume.id)
    resumes.setData(items.filter((item) => item.id !== resume.id))
  }

  return (
    <>
      <PageHeader
        eyebrow="My resumes"
        title="Your resumes"
        lead="Upload as many versions as you like — up to 25 — and score each of them against the jobs you are applying to."
      >
        <Link to="/analyses/new" className="btn btn-primary">
          <Sparkles size={15} aria-hidden="true" />
          New analysis
        </Link>
      </PageHeader>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
        <section aria-labelledby="library-heading" className="space-y-3">
          <h2 id="library-heading" className="eyebrow">
            Library {items.length > 0 ? <span data-numeric="">({items.length} of 25)</span> : null}
          </h2>

          {resumes.isLoading ? <SkeletonList rows={3} label="Loading your resumes" /> : null}

          {resumes.hasFailed ? (
            <ErrorState
              title="We could not load your resumes"
              error={resumes.error}
              onRetry={resumes.reload}
            />
          ) : null}

          {resumes.isReady && items.length === 0 ? (
            <EmptyState
              icon={FileText}
              title="No resumes yet"
              detail="Upload a PDF or DOCX and we will pull the text out of it. Nothing is shared, and you can delete it at any time."
            />
          ) : null}

          <ul className="space-y-3">
            {items.map((resume) => (
              <li key={resume.id} className="card flex items-start gap-4 p-5">
                <span
                  className={`mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${
                    resume.analysable ? 'bg-brand-600/10 text-brand-600' : 'bg-warning-500/10 text-warning-600'
                  }`}
                >
                  {resume.analysable ? (
                    <FileText size={17} aria-hidden="true" />
                  ) : (
                    <AlertTriangle size={17} aria-hidden="true" />
                  )}
                </span>

                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-semibold text-ink">
                    {resume.label || resume.originalFilename}
                  </p>
                  <p className="mt-1 truncate text-xs text-ink-subtle">
                    {resume.originalFilename} · {formatBytes(resume.fileSizeBytes)} · added{' '}
                    {formatRelative(resume.createdAt)}
                  </p>

                  {resume.analysable ? (
                    <p className="mt-2.5 text-xs text-ink-muted">
                      <span data-numeric="">{count(resume.wordCount, 'word')}</span> across{' '}
                      <span data-numeric="">{count(resume.pageCount, 'page')}</span>
                    </p>
                  ) : (
                    <p className="mt-2.5 text-xs text-warning-600">
                      {resume.extractionError ||
                        'We could not read any text from this file. If it is a scan, export a text-based PDF and upload that.'}
                    </p>
                  )}

                  {resume.textPreview ? (
                    <p className="mt-2.5 line-clamp-2 text-xs text-ink-subtle">{resume.textPreview}</p>
                  ) : null}
                </div>

                <ConfirmDelete
                  onConfirm={() => remove(resume)}
                  label={`Delete ${resume.label || resume.originalFilename}`}
                  question="Delete this resume?"
                />
              </li>
            ))}
          </ul>
        </section>

        <section aria-labelledby="upload-heading" className="lg:sticky lg:top-24 lg:self-start">
          <div className="panel p-5 sm:p-6">
            <h2 id="upload-heading" className="eyebrow">
              Add a resume
            </h2>
            <div className="mt-4">
              <UploadResumeForm
                onUploaded={(resume) => resumes.setData([resume, ...items])}
              />
            </div>
          </div>
        </section>
      </div>
    </>
  )
}
