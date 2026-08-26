import { Link } from 'react-router-dom'
import ThemeToggle from '../layout/ThemeToggle.jsx'
import { useAuth } from '../../features/auth/authContext.js'

/**
 * The bar on every public page.
 *
 * **It reads the session but never acts on it.** A signed-in visitor sees "Open your dashboard"
 * instead of two sign-up buttons, and that is the whole adaptation — no redirect. Bouncing
 * somebody off the landing page because they happen to be signed in makes the marketing copy
 * unreachable to the only people who can check whether it is true, and it breaks a shared link.
 *
 * Links here are routes, never anchors. An anchor to `#how-it-works` would be a dead link on
 * the demo and the not-found page, which use this same header.
 */
export default function SiteHeader() {
  const { isAuthenticated } = useAuth()

  return (
    <header className="border-b border-line">
      <div className="mx-auto flex h-16 max-w-6xl items-center gap-4 px-5 sm:px-8">
        <Link to="/" className="font-display text-lg font-semibold tracking-tight">
          Resume<span className="text-brand-600">IQ</span>
        </Link>

        <nav aria-label="Site" className="ml-auto flex items-center gap-1.5 sm:gap-2">
          <Link to="/demo" className="btn btn-ghost hidden sm:inline-flex">
            Sample analysis
          </Link>

          <ThemeToggle />

          {isAuthenticated ? (
            <Link to="/dashboard" className="btn btn-primary">
              Open your dashboard
            </Link>
          ) : (
            <>
              <Link to="/login" className="btn btn-ghost">
                Sign in
              </Link>
              <Link to="/signup" className="btn btn-primary">
                Create account
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  )
}
