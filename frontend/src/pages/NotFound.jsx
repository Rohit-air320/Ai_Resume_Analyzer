import { Link, useLocation } from 'react-router-dom'
import { Compass } from 'lucide-react'
import SiteHeader from '../components/marketing/SiteHeader.jsx'
import SiteFooter from '../components/marketing/SiteFooter.jsx'
import { useAuth } from '../features/auth/authContext.js'

/**
 * The page for an address that does not exist.
 *
 * **It replaces a redirect.** Until Phase 10 the catch-all route sent every unknown path to
 * `/`, which quietly rewrote the URL and left the visitor wondering whether they had mistyped
 * something or the link was dead. A wrong address is worth saying out loud — and it names the
 * path, because most of the time the reason is visible in it.
 *
 * The offer depends on the session: somebody signed in wants their dashboard, somebody who is
 * not wants the sample. Neither is a redirect, so a mistyped link never costs the reader their
 * place.
 */
export default function NotFound() {
  const location = useLocation()
  const { isAuthenticated } = useAuth()

  return (
    <div className="flex min-h-screen flex-col">
      <SiteHeader />

      <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col justify-center px-5 py-20 sm:px-8">
        <Compass size={22} className="text-ink-subtle" aria-hidden="true" />

        <p className="eyebrow mt-6">Not found</p>
        <h1 className="mt-3 text-display-md">There is nothing at this address.</h1>

        <p className="mt-4 text-sm leading-relaxed text-ink-muted">
          Nothing is broken — the path below just does not exist in this app. If you followed a
          link from somewhere, the link is out of date.
        </p>

        <p className="mt-5 break-all font-mono text-xs text-ink-subtle">{location.pathname}</p>

        <div className="mt-9 flex flex-wrap gap-3">
          {isAuthenticated ? (
            <Link to="/dashboard" className="btn btn-primary">
              Back to your dashboard
            </Link>
          ) : (
            <>
              <Link to="/" className="btn btn-primary">
                Back to the overview
              </Link>
              <Link to="/demo" className="btn btn-secondary">
                Read a sample analysis
              </Link>
            </>
          )}
        </div>
      </main>

      <SiteFooter />
    </div>
  )
}
