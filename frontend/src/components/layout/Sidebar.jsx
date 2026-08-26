import { NavLink } from 'react-router-dom'
import { ScanLine } from 'lucide-react'
import { NAV_SECTIONS } from './navItems.js'

/**
 * The navigation itself, rendered identically in the docked rail and the mobile drawer.
 *
 * The active item is marked by `NavLink`'s own `isActive`, not by comparing
 * `location.pathname` by hand — the router already knows, and hand-rolled matching is
 * where "/analyses" ends up highlighted while you are on "/analyses/new".
 *
 * `end` on the two list routes for the same reason in reverse: without it, "/analyses"
 * would light up as active whenever a child route like "/analyses/new" is open, so two
 * items would claim to be the current page.
 *
 * `aria-current="page"` comes from NavLink automatically, which is what makes the
 * highlight mean something to a screen reader rather than being decoration.
 */
export default function Sidebar({ onNavigate }) {
  return (
    <div className="flex h-full flex-col gap-8 border-r border-line bg-surface px-4 py-6">
      <NavLink
        to="/dashboard"
        onClick={onNavigate}
        className="flex items-center gap-2.5 px-2"
        aria-label="ResumeIQ home"
      >
        <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-600 text-white">
          <ScanLine size={17} aria-hidden="true" />
        </span>
        <span className="font-display text-base font-semibold tracking-tight">ResumeIQ</span>
      </NavLink>

      <nav aria-label="Main" className="flex-1 space-y-6">
        {NAV_SECTIONS.map((section, index) => (
          <div key={section.title ?? `section-${index}`}>
            {section.title ? (
              <p className="eyebrow px-2 pb-2">{section.title}</p>
            ) : null}

            <ul className="space-y-0.5">
              {section.items.map((item) => (
                <li key={item.to}>
                  {item.ready ? (
                    <NavLink
                      to={item.to}
                      end={item.to === '/analyses'}
                      onClick={onNavigate}
                      className={({ isActive }) => [
                        'flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm transition-colors duration-150',
                        isActive
                          ? 'bg-brand-600/10 font-medium text-brand-700'
                          : 'text-ink-muted hover:bg-surface-sunken hover:text-ink',
                      ].join(' ')}
                    >
                      <item.icon size={16} aria-hidden="true" />
                      {item.label}
                    </NavLink>
                  ) : (
                    <span
                      aria-disabled="true"
                      className="flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm text-ink-subtle"
                    >
                      <item.icon size={16} aria-hidden="true" />
                      {item.label}
                      <span className="ml-auto font-mono text-[0.625rem] uppercase tracking-wider text-ink-subtle">
                        Soon
                      </span>
                    </span>
                  )}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </nav>

      <p className="px-2 text-xs text-ink-subtle">
        Scores are computed on the server from your resume text. Nothing is published.
      </p>
    </div>
  )
}
