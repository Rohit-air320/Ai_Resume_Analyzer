import { useEffect, useRef, useState } from 'react'
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
 * **The drawer.** Below `lg` the rail becomes an overlay. A `hidden`/`block` toggle would
 * be four lines; this is longer because an overlay is a modal, and a modal has obligations.
 * It closes on Escape, because a full-screen panel with no visible way out is a trap. It
 * closes on navigation, since leaving it open over the page somebody just asked for is the
 * most common mobile-nav bug. It locks body scroll while open. Focus moves into it when it
 * opens and returns to the button that opened it when it closes — a keyboard user who is
 * sent into a dialog and dropped back at the top of the document has lost their place. And
 * Tab cycles inside it, because the page behind a modal is not reachable by pointer and
 * should not be reachable by keyboard either.
 *
 * **Focus after a navigation.** A single-page app replaces the page without telling anybody:
 * focus stays on the link that was clicked, so the next Tab continues through the navigation
 * and a screen reader announces nothing. Moving focus to `<main>` on every pathname change
 * puts the reader at the top of the new content, which is what a full page load would have
 * done. It is skipped on first render, where the browser has already done it.
 *
 * **The skip link** is the first thing in the tab order. Without it, reaching the page
 * content by keyboard means tabbing past every navigation item on every page.
 */

/** Everything a browser will focus, minus anything a parent has taken out of the order. */
const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

export default function AppLayout() {
  const [navOpen, setNavOpen] = useState(false)
  const location = useLocation()

  const mainRef = useRef(null)
  const drawerRef = useRef(null)
  const navTriggerRef = useRef(null)
  const firstRender = useRef(true)

  useEffect(() => setNavOpen(false), [location.pathname])

  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false
      return
    }
    mainRef.current?.focus()
  }, [location.pathname])

  useEffect(() => {
    if (!navOpen) return undefined

    // Captured at setup, because React has already detached the drawer and nulled the ref
    // by the time the cleanup below runs. Reading `drawerRef.current` there would silently
    // do nothing — which is the version of this effect that ESLint's ref-in-cleanup rule
    // caught, and it would have shipped as "focus is sometimes lost after closing".
    const drawer = drawerRef.current
    const trigger = navTriggerRef.current

    const onKeyDown = (event) => {
      if (event.key === 'Escape') {
        setNavOpen(false)
        return
      }
      if (event.key !== 'Tab') return

      const targets = drawer?.querySelectorAll(FOCUSABLE)
      if (!targets?.length) return

      const first = targets[0]
      const last = targets[targets.length - 1]

      // The wrap has to be done by hand: Tab from the last element in a dialog goes to
      // the browser chrome, not back to the top, and `inert` is not in every browser yet.
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }

    document.addEventListener('keydown', onKeyDown)
    document.body.style.overflow = 'hidden'
    drawer?.querySelector(FOCUSABLE)?.focus()

    return () => {
      document.removeEventListener('keydown', onKeyDown)
      document.body.style.overflow = ''

      // Removing the focused element drops focus to <body>, which is a keyboard user
      // stranded at the top of the document. Only reclaim it in that case: if something
      // else has taken focus deliberately — the route effect above moves it to <main>
      // when the drawer closed because of a navigation — that decision wins.
      const stranded = !document.activeElement || document.activeElement === document.body
      if (stranded) trigger?.focus()
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
          {/* The backdrop closes it on a tap. It is deliberately out of the accessibility
              tree: it used to carry the same "Close navigation" label as the button inside
              the panel, and two controls with one name is a defect for anybody navigating
              by name. The keyboard has Escape and the labelled close button. */}
          <button
            type="button"
            aria-hidden="true"
            tabIndex={-1}
            onClick={() => setNavOpen(false)}
            className="absolute inset-0 h-full w-full bg-ink/40 backdrop-blur-sm"
          />
          <div
            ref={drawerRef}
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
        <TopBar onOpenNav={() => setNavOpen(true)} navTriggerRef={navTriggerRef} />
        <main
          id="main"
          ref={mainRef}
          tabIndex={-1}
          data-focus-target=""
          className="mx-auto w-full max-w-6xl flex-1 px-4 py-7 sm:px-6 sm:py-9"
        >
          <Outlet />
        </main>
      </div>
    </div>
  )
}
