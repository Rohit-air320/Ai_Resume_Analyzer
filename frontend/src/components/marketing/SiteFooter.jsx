import { Link } from 'react-router-dom'

/**
 * The public footer.
 *
 * Deliberately thin. There is no company, no pricing and no blog, so inventing four columns of
 * links would be the clearest possible signal that this page was assembled from a template. What
 * is here is what exists: the two ways in, the sample, and the system check — which is a real
 * page a developer reading this project will want.
 */
export default function SiteFooter() {
  return (
    <footer className="border-t border-line">
      <div className="mx-auto flex max-w-6xl flex-col gap-6 px-5 py-10 sm:px-8 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="font-display text-base font-semibold tracking-tight">
            Resume<span className="text-brand-600">IQ</span>
          </p>
          <p className="mt-2 max-w-md text-sm text-ink-muted">
            Scores a resume against one job description, and explains every number it reports.
          </p>
        </div>

        <nav aria-label="Footer" className="flex flex-wrap gap-x-6 gap-y-2 text-sm">
          <Link to="/demo" className="text-ink-muted hover:text-ink">
            Sample analysis
          </Link>
          <Link to="/login" className="text-ink-muted hover:text-ink">
            Sign in
          </Link>
          <Link to="/signup" className="text-ink-muted hover:text-ink">
            Create account
          </Link>
          <Link to="/system-check" className="text-ink-muted hover:text-ink">
            System check
          </Link>
        </nav>
      </div>
    </footer>
  )
}
