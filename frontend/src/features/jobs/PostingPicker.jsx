import { useState } from 'react'
import { Briefcase, Plus } from 'lucide-react'
import ErrorState from '../../components/state/ErrorState.jsx'
import { SkeletonList } from '../../components/state/Skeleton.jsx'
import { formatRelative } from '../../lib/format.js'
import { useResource } from '../../lib/useResource.js'
import PostingForm from './PostingForm.jsx'
import { listPostings } from './jobApi.js'

/**
 * Pick a saved job description, or paste a new one.
 *
 * Same shape as the resume picker for the same reason — one resume gets measured against
 * many postings, and one posting gets measured against several drafts of a resume. Both
 * directions of that loop should cost one click.
 *
 * The preview is the first line or so of the posting rather than a title alone, because
 * two applications for "Backend Engineer" are indistinguishable by title and instantly
 * distinguishable by their opening sentence.
 */
export default function PostingPicker({ selectedId, onSelect }) {
  const [adding, setAdding] = useState(false)
  const postings = useResource(() => listPostings(), [])

  if (postings.isLoading) return <SkeletonList rows={2} label="Loading your job descriptions" />
  if (postings.hasFailed) {
    return (
      <ErrorState
        title="We could not load your job descriptions"
        error={postings.error}
        onRetry={postings.reload}
      />
    )
  }

  const items = postings.data ?? []

  function saved(posting) {
    // The server dedupes by content hash, so re-pasting returns the existing row rather
    // than a duplicate — which means this list must not blindly prepend.
    const known = items.some((item) => item.id === posting.id)
    postings.setData(known ? items : [posting, ...items])
    setAdding(false)
    onSelect(posting)
  }

  return (
    <div className="space-y-4">
      {items.length > 0 ? (
        <ul className="space-y-2.5">
          {items.map((posting) => {
            const chosen = posting.id === selectedId

            return (
              <li key={posting.id}>
                <label
                  className={[
                    'flex cursor-pointer items-start gap-3 rounded-card border px-4 py-3.5 transition-colors duration-150',
                    chosen ? 'border-brand-500 bg-brand-600/5' : 'border-line hover:border-line-strong',
                  ].join(' ')}
                >
                  <input
                    type="radio"
                    name="posting"
                    className="mt-1 h-4 w-4 accent-brand-600"
                    checked={chosen}
                    onChange={() => onSelect(posting)}
                  />

                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium text-ink">
                      {posting.title}
                      {posting.company ? (
                        <span className="font-normal text-ink-muted"> · {posting.company}</span>
                      ) : null}
                    </span>
                    <span className="mt-1 block line-clamp-2 text-xs text-ink-subtle">
                      {posting.text}
                    </span>
                    <span className="mt-1.5 block text-xs text-ink-subtle">
                      Saved {formatRelative(posting.createdAt)}
                    </span>
                  </span>

                  <Briefcase size={16} className="mt-0.5 shrink-0 text-ink-subtle" aria-hidden="true" />
                </label>
              </li>
            )
          })}
        </ul>
      ) : (
        <p className="text-sm text-ink-muted">
          No job descriptions saved yet. Paste the one you are applying to below.
        </p>
      )}

      {adding || items.length === 0 ? (
        <div className="card p-5">
          <p className="eyebrow">Paste a job description</p>
          <div className="mt-4">
            <PostingForm onSaved={saved} />
          </div>
        </div>
      ) : (
        <button type="button" onClick={() => setAdding(true)} className="btn btn-secondary">
          <Plus size={15} aria-hidden="true" />
          Paste another job description
        </button>
      )}
    </div>
  )
}
