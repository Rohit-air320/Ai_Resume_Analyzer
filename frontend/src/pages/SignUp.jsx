import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import AuthLayout from '../features/auth/AuthLayout.jsx'
import { useAuth } from '../features/auth/authContext.js'
import TextField from '../components/form/TextField.jsx'
import FormError from '../components/form/FormError.jsx'
import { NO_FAILURE, describeFailure } from '../lib/formErrors.js'

/**
 * Create an account.
 *
 * Unlike sign-in, this form does state the rules — a password field that rejects an
 * entry without saying why is a person guessing at a length. The hints match the
 * server's constraints exactly (8 to 72 characters, a name of at least two), because
 * a client rule the server does not share is a rule somebody will hit from the API
 * and a client rule stricter than the server's is a field nobody can fill.
 *
 * The 72 is BCrypt's limit, not a preference: it hashes at most 72 bytes and ignores
 * the rest, so accepting a longer passphrase would silently mean only its first 72
 * characters ever mattered.
 */
export default function SignUp() {
  const { signUp, isAuthenticated, isLoading } = useAuth()
  const navigate = useNavigate()

  const [form, setForm] = useState({ fullName: '', email: '', password: '' })
  const [failure, setFailure] = useState(NO_FAILURE)
  const [submitting, setSubmitting] = useState(false)

  if (!isLoading && isAuthenticated) {
    return <Navigate to="/dashboard" replace />
  }

  const update = (field) => (event) => {
    setForm((previous) => ({ ...previous, [field]: event.target.value }))
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    setFailure(NO_FAILURE)
    setSubmitting(true)

    try {
      await signUp(form)
      // Registration signs you in, so there is nothing to ask for twice.
      navigate('/dashboard', { replace: true })
    } catch (error) {
      setFailure(describeFailure(error))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout
      eyebrow="Get started"
      title="Create your account"
      subtitle="Upload a resume, paste a job description, and see how the two line up."
      footer={
        <>
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-brand-600 hover:text-brand-700">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} noValidate className="space-y-5">
        <FormError message={failure.message} />

        <TextField
          label="Full name"
          name="fullName"
          value={form.fullName}
          onChange={update('fullName')}
          error={failure.fieldErrors.fullName}
          autoComplete="name"
          required
        />

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
          hint="At least 8 characters."
          autoComplete="new-password"
          required
        />

        <button type="submit" className="btn btn-primary w-full" disabled={submitting}>
          {submitting && <Loader2 size={16} className="animate-spin" aria-hidden="true" />}
          {submitting ? 'Creating your account…' : 'Create account'}
        </button>
      </form>

      <p className="mt-6 text-xs text-ink-subtle">
        Your resume is stored privately and is never shared. You can delete it, and every
        analysis of it, at any time.
      </p>
    </AuthLayout>
  )
}
