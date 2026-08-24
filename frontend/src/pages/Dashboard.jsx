import { Link } from 'react-router-dom'
import { FileText, LogOut, Sparkles } from 'lucide-react'
import { useAuth } from '../features/auth/authContext.js'

/**
 * Placeholder dashboard — the landing target for a signed-in session.
 *
 * Phase 7 replaces this with the real thing: score history, recent analyses, skill
 * gaps and the sidebar. What it proves today is the whole point of Phase 3, and it
 * proves it by rendering: the name below came from `/api/auth/me` via a token this
 * page never sees, and it survives a browser refresh because the httpOnly cookie
 * renews the session before this component mounts.
 */
export default function Dashboard() {
  const { user, signOut } = useAuth()

  return (
    <main className="mx-auto w-full max-w-3xl px-5 py-14 sm:px-8 sm:py-20">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="eyebrow">Signed in</p>
          <h1 className="mt-3 text-display-md">
            Welcome, {user?.fullName?.split(' ')[0] || 'there'}.
          </h1>
          <p className="mt-3 max-w-lg text-ink-muted">
            Your session is live. Uploading, job descriptions and analysis arrive in the next
            phases; this page becomes the real dashboard in Phase 7.
          </p>
        </div>

        <button type="button" onClick={signOut} className="btn btn-secondary">
          <LogOut size={16} aria-hidden="true" />
          Sign out
        </button>
      </header>

      <section className="panel mt-10 p-6" aria-labelledby="account-heading">
        <h2 id="account-heading" className="text-base font-semibold">
          Account
        </h2>
        <dl className="mt-5 grid gap-x-8 gap-y-3 sm:grid-cols-2">
          <Detail label="Name" value={user?.fullName} />
          <Detail label="Email" value={user?.email} />
          <Detail label="Target role" value={user?.targetRole || 'Not set yet'} />
          <Detail label="Experience" value={user?.experienceLevel || 'Not set yet'} />
        </dl>
      </section>

      <section className="mt-6 grid gap-4 sm:grid-cols-2">
        <NextStep
          icon={FileText}
          phase="Phase 4"
          title="Upload a resume"
          detail="PDF or DOCX, read on the server and never exposed as a public file."
        />
        <NextStep
          icon={Sparkles}
          phase="Phase 6"
          title="Analyse against a posting"
          detail="ATS score, match score, skill gaps and suggestions tied to real evidence."
        />
      </section>

      <p className="mt-8 text-xs text-ink-subtle">
        Setup check still available at{' '}
        <Link to="/system-check" className="underline underline-offset-2 hover:text-ink-muted">
          /system-check
        </Link>
        .
      </p>
    </main>
  )
}

function Detail({ label, value }) {
  return (
    <div>
      <dt className="eyebrow">{label}</dt>
      <dd className="mt-1 text-sm text-ink">{value || '—'}</dd>
    </div>
  )
}

function NextStep({ icon: Icon, phase, title, detail }) {
  return (
    <article className="card p-5">
      <Icon size={18} className="text-brand-600" aria-hidden="true" />
      <p className="eyebrow mt-4">{phase}</p>
      <h3 className="mt-2 text-sm font-semibold">{title}</h3>
      <p className="mt-2 text-sm text-ink-muted">{detail}</p>
    </article>
  )
}
