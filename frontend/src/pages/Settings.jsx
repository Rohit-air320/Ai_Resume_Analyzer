import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Activity, ArrowUpRight, LogOut, Monitor, Moon, ShieldCheck, Sun } from 'lucide-react'
import PageHeader from '../components/layout/PageHeader.jsx'
import { useAuth } from '../features/auth/authContext.js'
import { useTheme } from '../features/theme/themeContext.js'

/**
 * Settings.
 *
 * The rule this page is written to: every control here does something, and everything it
 * cannot do is stated rather than mocked up. A settings screen is where portfolio projects
 * traditionally grow toggles for email digests that send nothing and a "delete account"
 * button wired to a `TODO`. Two real preferences, one real action, and three honest
 * statements is a smaller page and a truthful one.
 *
 * **Theme is three options, not a switch.** The top bar's switch commits to light or dark;
 * this is where "follow my system" lives, which is the default and the only way back to it.
 * The radios are native inputs — visually hidden, with the card as their label — because a
 * `div` with `role="radio"` has to reimplement arrow-key navigation, and the browser already
 * ships that behaviour for free.
 *
 * **Motion has no control on purpose.** Reduced motion is an operating-system setting that
 * every animation in this app already honours through one rule in `index.css`. A duplicate
 * toggle here would be a second source of truth for a preference the person has already
 * expressed, so the page reports what it detected instead and says where to change it.
 */

const THEME_OPTIONS = [
  {
    value: 'system',
    label: 'Follow my system',
    detail: 'Matches your operating system, and changes with it while the tab is open.',
    icon: Monitor,
  },
  {
    value: 'light',
    label: 'Light',
    detail: 'A cool paper white. Best in a bright room or on a projector.',
    icon: Sun,
  },
  {
    value: 'dark',
    label: 'Dark',
    detail: 'Near-black with lifted indigos, so long reports stay readable at night.',
    icon: Moon,
  },
]

/** True when the operating system asks for less animation. Read live, not once. */
function useReducedMotion() {
  const [reduced, setReduced] = useState(
    () => Boolean(window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches),
  )

  useEffect(() => {
    const media = window.matchMedia?.('(prefers-reduced-motion: reduce)')
    if (!media) return undefined

    const onChange = (event) => setReduced(event.matches)

    media.addEventListener?.('change', onChange)
    if (!media.addEventListener) media.addListener?.(onChange)

    return () => {
      media.removeEventListener?.('change', onChange)
      if (!media.removeEventListener) media.removeListener?.(onChange)
    }
  }, [])

  return reduced
}

export default function Settings() {
  const { theme, preference, setPreference } = useTheme()
  const { signOut } = useAuth()
  const reducedMotion = useReducedMotion()

  return (
    <>
      <PageHeader
        eyebrow="Settings"
        title="Preferences"
        lead="How ResumeIQ looks on this device, what it keeps, and how to end this session."
      />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div className="space-y-6">
          <section className="panel p-5 sm:p-7" aria-labelledby="appearance-heading">
            <h2 id="appearance-heading" className="eyebrow">
              Appearance
            </h2>

            <fieldset className="mt-4">
              <legend className="field-label">Theme</legend>
              <p className="mt-1 text-sm text-ink-muted">
                Saved in this browser. Applied before the first paint, so the page never flashes
                the wrong theme on the way back.
              </p>

              <div className="mt-4 space-y-2.5">
                {THEME_OPTIONS.map((option) => {
                  const selected = preference === option.value

                  return (
                    <label
                      key={option.value}
                      className="block cursor-pointer"
                    >
                      <input
                        type="radio"
                        name="theme"
                        value={option.value}
                        checked={selected}
                        onChange={() => setPreference(option.value)}
                        className="peer sr-only"
                      />
                      <span
                        className={[
                          'flex items-start gap-3 rounded-card border p-3.5 transition-colors duration-150',
                          'peer-focus-visible:ring-2 peer-focus-visible:ring-brand-500 peer-focus-visible:ring-offset-2 peer-focus-visible:ring-offset-bg',
                          selected
                            ? 'border-brand-500 bg-brand-600/8'
                            : 'border-line bg-surface-sunken hover:border-line-strong',
                        ].join(' ')}
                      >
                        <span
                          aria-hidden="true"
                          className={[
                            'mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg',
                            selected ? 'bg-brand-600 text-white' : 'bg-surface text-ink-subtle',
                          ].join(' ')}
                        >
                          <option.icon size={16} />
                        </span>
                        <span className="min-w-0">
                          <span className="block text-sm font-medium text-ink">{option.label}</span>
                          <span className="mt-0.5 block text-xs text-ink-muted">{option.detail}</span>
                        </span>
                      </span>
                    </label>
                  )
                })}
              </div>
            </fieldset>

            {/* Worth stating, because "follow my system" gives no clue which side it landed on. */}
            <p className="mt-4 border-t border-line pt-4 text-sm text-ink-muted">
              Showing the <span className="font-medium text-ink">{theme}</span> theme now.
            </p>
          </section>

          <section className="panel p-5 sm:p-7" aria-labelledby="motion-heading">
            <h2 id="motion-heading" className="eyebrow">
              Motion
            </h2>

            <div className="mt-4 flex items-start gap-3">
              <span
                aria-hidden="true"
                className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-surface-sunken text-ink-subtle"
              >
                <Activity size={16} />
              </span>
              <div className="min-w-0 text-sm">
                <p className="font-medium text-ink">
                  {reducedMotion
                    ? 'Your system asks for reduced motion, so animations are off.'
                    : 'Animations are on, and follow your system setting.'}
                </p>
                <p className="mt-1 text-ink-muted">
                  There is no switch here on purpose. Reduced motion is a system preference, and
                  every transition in ResumeIQ already honours it — a second toggle would only be
                  a second place for the two answers to disagree. Change it in your operating
                  system’s accessibility settings.
                </p>
              </div>
            </div>
          </section>

          <section className="panel p-5 sm:p-7" aria-labelledby="session-heading">
            <h2 id="session-heading" className="eyebrow">
              Session
            </h2>

            <p className="mt-4 text-sm text-ink-muted">
              Signing out ends this session on the server as well as in this tab, so the refresh
              token that keeps you signed in cannot be used again. Your resumes and analyses are
              kept.
            </p>

            <button type="button" onClick={signOut} className="btn btn-secondary mt-4">
              <LogOut size={16} aria-hidden="true" />
              Sign out
            </button>
          </section>
        </div>

        <div className="space-y-6">
          <section className="card p-5" aria-labelledby="data-heading">
            <h2 id="data-heading" className="eyebrow">
              What is stored
            </h2>

            <ul className="mt-4 space-y-3 text-sm text-ink-muted">
              <li>
                <span className="block font-medium text-ink">Your resume files and their text</span>
                Extracted once on upload, so an analysis never re-reads the file.
              </li>
              <li>
                <span className="block font-medium text-ink">Job descriptions you paste</span>
                Kept so a later analysis can reuse the same posting.
              </li>
              <li>
                <span className="block font-medium text-ink">Scores and advice</span>
                Each analysis records which writer produced its words.
              </li>
            </ul>

            <p className="mt-4 border-t border-line pt-4 text-sm text-ink-muted">
              Nothing is published, and no resume text appears in a URL. Deleting a resume deletes
              the stored file with it.
            </p>

            <div className="mt-4 flex flex-wrap gap-2">
              <Link to="/resumes" className="btn btn-secondary px-3 py-2 text-xs">
                Manage resumes
                <ArrowUpRight size={14} aria-hidden="true" />
              </Link>
              <Link to="/analyses" className="btn btn-secondary px-3 py-2 text-xs">
                Analysis history
                <ArrowUpRight size={14} aria-hidden="true" />
              </Link>
            </div>
          </section>

          <section className="card p-5" aria-labelledby="account-heading">
            <h2 id="account-heading" className="eyebrow">
              Account
            </h2>

            <p className="mt-4 text-sm text-ink-muted">
              Your name, target role and experience level live on your profile — they shape how an
              analysis is framed.
            </p>

            <Link to="/profile" className="btn btn-secondary mt-4 px-3 py-2 text-xs">
              Edit your profile
              <ArrowUpRight size={14} aria-hidden="true" />
            </Link>

            <p className="mt-4 border-t border-line pt-4 text-xs text-ink-muted">
              Closing an account is not self-serve yet. Deleting every resume removes the files and
              the text behind your analyses.
            </p>
          </section>

          <section className="card p-5" aria-labelledby="diagnostics-heading">
            <h2 id="diagnostics-heading" className="eyebrow">
              Diagnostics
            </h2>

            <div className="mt-4 flex items-start gap-3 text-sm">
              <span
                aria-hidden="true"
                className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-surface-sunken text-ink-subtle"
              >
                <ShieldCheck size={16} />
              </span>
              <p className="min-w-0 text-ink-muted">
                The connection check reports whether this browser can reach the API and which
                profile the server is running. It reads no account data.
              </p>
            </div>

            <Link to="/system-check" className="btn btn-secondary mt-4 px-3 py-2 text-xs">
              Run the connection check
              <ArrowUpRight size={14} aria-hidden="true" />
            </Link>
          </section>
        </div>
      </div>
    </>
  )
}
