import { useEffect, useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { X } from 'lucide-react'
import Sidebar from './Sidebar.jsx'
import TopBar from './TopBar.jsx'

/**
 * The frame every signed-in page renders inside.
 *
 * Used as a layout route nested under the auth guard, so a new page joins the shell by
 * being written in the right block of the route table — it cannot forget the sidebar,
 * and the sidebar does not have to know what pages exist.
 *
 * **The drawer.** Below `lg` the rail becomes an overlay. Three things it does that a
 * `hidden`/`block` toggle would not: it closes on Escape, because a full-screen overlay
 * with no visible way out is a trap; it closes on navigation, since leaving it open over
 * the page somebody just asked for is the most common mobile-nav bug; and it locks body
 * scroll while open, so the page behind does not slide under a fixed panel. The docked
 * rail is a separate element rather than the same one repositioned, which keeps its
 * markup free of the drawer's state.
 *
 * **The skip link** is the first thing in the tab order. Without it, reaching the page
 * content by keyboard means tabbing past every navigation item on every page.
 */
export default function AppLayout() {
  const [navOpen, setNavOpen] = useState(false)
  const location = useLocation()

  useEffect(() => setNavOpen(false), [location.pathname])

  useEffect(() => {
    if (!navOpen) return undefined

    const onKeyDown = (event) => {
      if (event.key === 'Escape') setNavOpen(false)
    }
    document.addEventListener('keydown', onKeyDown)
    document.body.style.overflow = 'hidden'

    return () => {
      document.removeEventListener('keydown', onKeyDown)
      document.body.style.overflow = ''
    }
  }, [navOpen])

  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[248px_minmax(0,1fr)]">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-brand-600 focus:px-4 focus:py-2 focus:text-sm focus:text-white"
      >
        Skip to content
      </a>

      {/* Docked rail: its own scroll region, so a long nav cannot push the page down. */}
      <div className="hidden lg:sticky lg:top-0 lg:block lg:h-screen lg:overflow-y-auto">
        <Sidebar />
      </div>

      {navOpen ? (
        <div className="fixed inset-0 z-40 lg:hidden">
          {/* Clicking the backdrop closes it, and it is a button so the keyboard can too. */}
          <button
            type="button"
            aria-label="Close navigation"
            onClick={() => setNavOpen(false)}
            className="absolute inset-0 h-full w-full bg-ink/40 backdrop-blur-sm"
          />
          <div
            role="dialog"
            aria-modal="true"
            aria-label="Navigation"
            className="relative h-full w-[264px] max-w-[82vw] animate-fade-up overflow-y-auto shadow-raised"
          >
            <button
              type="button"
              onClick={() => setNavOpen(false)}
              className="btn btn-ghost absolute right-3 top-4 px-2 py-1.5"
              aria-label="Close navigation"
            >
              <X size={17} aria-hidden="true" />
            </button>
            <Sidebar onNavigate={() => setNavOpen(false)} />
          </div>
        </div>
      ) : null}

      <div className="flex min-w-0 flex-col">
        <TopBar onOpenNav={() => setNavOpen(true)} />
        <main id="main" className="mx-auto w-full max-w-6xl flex-1 px-4 py-7 sm:px-6 sm:py-9">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
