import { LogOut, Menu } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../features/auth/authContext.js'
import ThemeToggle from './ThemeToggle.jsx'

/**
 * The bar above the page: the drawer trigger on small screens, the theme switch, and
 * who is signed in.
 *
 * The account area is a link to the profile plus a separate sign-out button, not a
 * dropdown. A menu is the reflex, and for two items it is the wrong trade — a correct
 * one needs focus management, escape handling, click-outside detection and roving
 * arrow keys, all so that two things can hide behind a chevron. Both are visible
 * instead, and the name doubles as the way into the profile.
 *
 * The email is hidden below `sm` while the name stays, because on a narrow screen the
 * bar has room for one of them and the name is the one that identifies the account to
 * its owner.
 */
export default function TopBar({ onOpenNav }) {
  const { user, signOut } = useAuth()

  return (
    <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b border-line bg-bg/85 px-4 backdrop-blur sm:px-6">
      <button
        type="button"
        onClick={onOpenNav}
        className="btn btn-ghost px-2.5 py-2 lg:hidden"
        aria-label="Open navigation"
      >
        <Menu size={18} aria-hidden="true" />
      </button>

      <div className="ml-auto flex items-center gap-1.5">
        <ThemeToggle />

        <Link
          to="/profile"
          className="flex items-center gap-2.5 rounded-lg px-2 py-1.5 hover:bg-surface-sunken"
        >
          <span
            aria-hidden="true"
            className="flex h-7 w-7 items-center justify-center rounded-full bg-brand-600/12 font-mono text-xs font-semibold text-brand-700"
          >
            {initialsOf(user?.fullName)}
          </span>
          <span className="hidden text-left leading-tight xs:block">
            <span className="block text-sm font-medium text-ink">{user?.fullName || 'Your account'}</span>
            <span className="hidden text-xs text-ink-subtle sm:block">{user?.email}</span>
          </span>
        </Link>

        <button
          type="button"
          onClick={signOut}
          className="btn btn-ghost px-2.5 py-2"
          aria-label="Sign out"
        >
          <LogOut size={16} aria-hidden="true" />
        </button>
      </div>
    </header>
  )
}

/** "Priya Raman" → "PR". Falls back to one letter, then to a neutral glyph. */
function initialsOf(fullName) {
  const parts = String(fullName || '').trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '·'
  if (parts.length === 1) return parts[0].charAt(0).toUpperCase()
  return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase()
}
