import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { useAuth } from './authContext.js'

/**
 * Route guard for everything behind a sign-in.
 *
 * Used as a layout route, so a whole subtree is protected by one line in the route
 * table rather than by each page remembering to check. The guard is a convenience,
 * not the security boundary — the API authorises every request on its own, and this
 * only decides what to render.
 *
 * The three-way state is the part worth getting right. On a page load the session is
 * not yet known, because it is being renewed from the httpOnly cookie. Treating that
 * moment as "signed out" would bounce a signed-in person to the login screen on
 * every refresh, so it renders a waiting state instead and only redirects once the
 * answer is actually "no".
 */
export default function RequireAuth() {
  const { isLoading, isAuthenticated } = useAuth()
  const location = useLocation()

  if (isLoading) {
    return <RestoringSession />
  }

  if (!isAuthenticated) {
    // Where they were headed, carried along so signing in finishes the journey
    // instead of dumping them on the dashboard. `replace` keeps the guarded URL out
    // of history, so the back button does not walk into a redirect loop.
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  return <Outlet />
}

function RestoringSession() {
  return (
    <div className="flex min-h-screen items-center justify-center px-6">
      <p className="flex items-center gap-3 text-sm text-ink-muted" role="status">
        <Loader2 size={18} className="animate-spin" aria-hidden="true" />
        Restoring your session…
      </p>
    </div>
  )
}
