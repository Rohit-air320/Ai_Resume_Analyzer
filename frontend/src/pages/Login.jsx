import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import AuthLayout from '../features/auth/AuthLayout.jsx'
import { useAuth } from '../features/auth/authContext.js'
import TextField from '../components/form/TextField.jsx'
import FormError from '../components/form/FormError.jsx'
import { NO_FAILURE, describeFailure } from '../lib/formErrors.js'

/**
 * Sign in.
 *
 * The form does no validation of its own beyond `required`, and that is deliberate.
 * A sign-in form that says "password must be at least 8 characters" has just told a
 * stranger what the stored password satisfies, and one that rejects a malformed
 * address before sending it gives a cheap way to probe which addresses are worth
 * attacking. Everything is one answer from the server, in the server's words.
 *
 * Redirect handling: a person sent here by the route guard arrives with the URL they
 * wanted in `location.state.from`, and signing in finishes that journey instead of
 * dropping them on the dashboard.
 */
export default function Login() {
  const { signIn, isAuthenticated, isLoading } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [form, setForm] = useState({ email: '', password: '' })
  const [failure, setFailure] = useState(NO_FAILURE)
  const [submitting, setSubmitting] = useState(false)

  const destination = location.state?.from?.pathname || '/dashboard'

  // Someone already signed in has no business on this page — reaching it by typing
  // the URL or pressing back should land them where they were going.
  if (!isLoading && isAuthenticated) {
    return <Navigate to={destination} replace />
  }

  const update = (field) => (event) => {
    setForm((previous) => ({ ...previous, [field]: event.target.value }))
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    setFailure(NO_FAILURE)
    setSubmitting(true)

    try {
      await signIn(form)
      navigate(destination, { replace: true })
    } catch (error) {
      setFailure(describeFailure(error))
      // Only the password is cleared. Retyping an email that was probably right is
      // an irritation; leaving a wrong password in the box invites the same failure
      // twice, and after five of those the account is locked for fifteen minutes.
      setForm((previous) => ({ ...previous, password: '' }))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout
      eyebrow="Welcome back"
      title="Sign in to ResumeIQ"
      subtitle="Pick up where you left off — your analyses and resumes are waiting."
      footer={
        <>
          New here?{' '}
          <Link to="/signup" className="font-medium text-brand-600 hover:text-brand-700">
            Create an account
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} noValidate className="space-y-5">
        <FormError message={failure.message} />

        <TextField
          label="Email"
          name="email"
          type="email"
          value={form.email}
          onChange={update('email')}
          error={failure.fieldErrors.email}
          autoComplete="email"
          required
        />

        <TextField
          label="Password"
          name="password"
          type="password"
          value={form.password}
          onChange={update('password')}
          error={failure.fieldErrors.password}
          autoComplete="current-password"
          required
        />

        <button type="submit" className="btn btn-primary w-full" disabled={submitting}>
          {submitting && <Loader2 size={16} className="animate-spin" aria-hidden="true" />}
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>

      <p className="mt-6 text-xs text-ink-subtle">
        Want to look around first?{' '}
        <Link to="/" className="text-ink-muted underline underline-offset-2 hover:text-ink">
          Try the demo
        </Link>{' '}
        — no account needed.
      </p>
    </AuthLayout>
  )
}
