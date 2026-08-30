import { useCallback, useEffect, useState } from 'react'
import { AlertTriangle, CheckCircle2, Loader2, Moon, RefreshCw, Sun } from 'lucide-react'
import { apiClient } from '../lib/apiClient.js'
import { SCORE_BANDS } from '../lib/scoreBands.js'
import useDocumentTitle from '../lib/useDocumentTitle.js'

/**
 * Phase 1 setup check.
 *
 * It exists to prove three things at a glance: the React build works, the API answers,
 * and the design tokens resolve in both themes. Phase 10 replaces "/" with the real
 * landing page and this stays available at /system-check.
 */

const STATUS = {
  LOADING: 'loading',
  CONNECTED: 'connected',
  FAILED: 'failed',
}

function useTheme() {
  const [isDark, setIsDark] = useState(() => document.documentElement.classList.contains('dark'))

  const toggle = useCallback(() => {
    setIsDark((previous) => {
      const next = !previous
      document.documentElement.classList.toggle('dark', next)
      try {
        window.localStorage.setItem('resumeiq.theme', next ? 'dark' : 'light')
      } catch {
        // Storage blocked. The class is already applied, so the toggle still works.
      }
      return next
    })
  }, [])

  return { isDark, toggle }
}

export default function SystemCheck() {
  useDocumentTitle('System check')
  const [status, setStatus] = useState(STATUS.LOADING)
  const [health, setHealth] = useState(null)
  const [error, setError] = useState(null)
  const { isDark, toggle } = useTheme()

  const checkApi = useCallback(async () => {
    setStatus(STATUS.LOADING)
    setError(null)
    try {
      const { data } = await apiClient.get('/health')
      setHealth(data)
      setStatus(STATUS.CONNECTED)
    } catch (apiError) {
      setError(apiError)
      setStatus(STATUS.FAILED)
    }
  }, [])

  useEffect(() => {
    checkApi()
  }, [checkApi])

  return (
    <main id="main" className="mx-auto w-full max-w-3xl px-5 py-14 sm:px-8 sm:py-20">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="eyebrow">Phase 1 · Setup</p>
          <h1 className="mt-3 text-display-md">ResumeIQ is wired up.</h1>
          <p className="mt-3 max-w-lg text-ink-muted">
            Frontend build, API connection and design tokens, all verifiable on one page. The
            landing page replaces this route in Phase 10.
          </p>
        </div>
        <button type="button" onClick={toggle} className="btn btn-secondary" aria-pressed={isDark}>
          {isDark ? <Sun size={16} aria-hidden="true" /> : <Moon size={16} aria-hidden="true" />}
          {isDark ? 'Light' : 'Dark'}
        </button>
      </header>

      <section className="panel mt-10 p-6" aria-labelledby="api-heading">
        <div className="flex items-center justify-between gap-4">
          <h2 id="api-heading" className="text-base font-semibold">
            API connection
          </h2>
          <button type="button" onClick={checkApi} className="btn btn-ghost -mr-2 px-2 py-1 text-xs">
            <RefreshCw size={14} aria-hidden="true" />
            Check again
          </button>
        </div>

        <div className="mt-5" aria-live="polite">
          {status === STATUS.LOADING && (
            <div className="flex items-center gap-3 text-ink-muted">
              <Loader2 size={18} className="animate-spin" aria-hidden="true" />
              <span className="text-sm">Contacting the API…</span>
            </div>
          )}

          {status === STATUS.CONNECTED && health && (
            <div>
              <p className="flex items-center gap-2 text-sm font-medium text-band-excellent">
                <CheckCircle2 size={18} aria-hidden="true" />
                Connected
              </p>
              <dl className="mt-4 grid gap-x-8 gap-y-3 sm:grid-cols-2">
                <Detail label="Application" value={health.application} />
                <Detail label="Version" value={health.version} />
                <Detail label="Status" value={health.status} />
                <Detail
                  label="Profiles"
                  value={health.activeProfiles?.length ? health.activeProfiles.join(', ') : 'default'}
                />
              </dl>
            </div>
          )}

          {status === STATUS.FAILED && error && (
            <div>
              <p className="flex items-center gap-2 text-sm font-medium text-band-critical">
                <AlertTriangle size={18} aria-hidden="true" />
                {error.message}
              </p>
              <p className="mt-3 text-sm text-ink-muted">
                Start the API from the <span className="font-mono text-xs">backend</span> folder with{' '}
                <span className="font-mono text-xs text-ink">mvn spring-boot:run</span>, then check again.
              </p>
              <p className="mt-2 font-mono text-xs text-ink-subtle">code: {error.code}</p>
            </div>
          )}
        </div>
      </section>

      <section className="panel mt-6 p-6" aria-labelledby="type-heading">
        <h2 id="type-heading" className="text-base font-semibold">
          Type scale
        </h2>
        <div className="mt-5 space-y-4">
          <p className="font-display text-display-md">Know how your resume performs</p>
          <p className="text-ink-muted">
            Instrument Sans carries body copy and interface labels at a comfortable reading weight.
          </p>
          <p className="font-mono text-sm text-ink-muted">
            JetBrains Mono 0123456789 — scores, keywords, axis labels
          </p>
        </div>
      </section>

      <section className="panel mt-6 p-6" aria-labelledby="bands-heading">
        <h2 id="bands-heading" className="text-base font-semibold">
          Score bands
        </h2>
        <p className="mt-2 text-sm text-ink-muted">
          One place decides what a number means, so no component invents its own idea of good.
        </p>
        <ul className="mt-5 space-y-2.5">
          {SCORE_BANDS.map((band) => (
            <li key={band.id} className="flex items-center gap-4">
              <span
                className={`h-8 w-1.5 shrink-0 rounded-full ${band.bg}`}
                aria-hidden="true"
              />
              <span data-numeric className="w-16 shrink-0 text-sm text-ink-muted">
                {band.min}–{band.max}
              </span>
              <span className={`text-sm font-medium ${band.text}`}>{band.label}</span>
            </li>
          ))}
        </ul>
      </section>
    </main>
  )
}

function Detail({ label, value }) {
  return (
    <div>
      <dt className="eyebrow">{label}</dt>
      <dd data-numeric className="mt-1 text-sm text-ink">
        {value}
      </dd>
    </div>
  )
}
