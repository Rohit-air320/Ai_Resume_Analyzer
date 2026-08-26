import { useEffect, useState } from 'react'
import { Check, Loader2, Save } from 'lucide-react'
import PageHeader from '../components/layout/PageHeader.jsx'
import FormError from '../components/form/FormError.jsx'
import TextField from '../components/form/TextField.jsx'
import ErrorState from '../components/state/ErrorState.jsx'
import { SkeletonList } from '../components/state/Skeleton.jsx'
import { useAuth } from '../features/auth/authContext.js'
import { EXPERIENCE_LEVELS, fetchProfile, updateProfile } from '../features/profile/profileApi.js'
import { formatDate, formatDateTime, humanise } from '../lib/format.js'
import { describeFailure } from '../lib/formErrors.js'
import { useResource } from '../lib/useResource.js'

/**
 * Your profile.
 *
 * Three editable fields, and a deliberate line between them and everything else. The
 * email is shown but not editable, because changing it is an identity change that needs
 * a verification flow rather than a text input — pretending otherwise in the UI would
 * promise something the API does not do.
 *
 * **Target role and experience level are not decoration.** They are what the dashboard
 * uses to say who you are aiming at, and what future recommendations weigh against. The
 * hint text says so, because a field whose effect is invisible is a field people leave
 * blank.
 *
 * The PUT replaces rather than patches, so the form always submits all three values —
 * including the ones the person did not touch. Sending only the changed field would
 * silently clear the others.
 */

const EXPERIENCE_HINTS = {
  ENTRY: 'Student, or no professional experience yet',
  JUNIOR: 'Up to about two years',
  MID: 'Two to five years',
  SENIOR: 'Five years or more',
  LEAD: 'Leading a team or owning a system',
}

export default function Profile() {
  const { applyUser } = useAuth()
  const profile = useResource(() => fetchProfile(), [])

  const [form, setForm] = useState({ fullName: '', targetRole: '', experienceLevel: '' })
  const [pending, setPending] = useState(false)
  const [saved, setSaved] = useState(false)
  const [failure, setFailure] = useState({ message: null, fieldErrors: {} })

  // The form is seeded once the profile lands. `non_null` inclusion means an unset field
  // is absent from the response rather than null, so every value needs a string fallback
  // or React switches the input to uncontrolled halfway through its life.
  useEffect(() => {
    if (!profile.data) return
    setForm({
      fullName: profile.data.fullName ?? '',
      targetRole: profile.data.targetRole ?? '',
      experienceLevel: profile.data.experienceLevel ?? '',
    })
  }, [profile.data])

  function update(field) {
    return (event) => {
      setSaved(false)
      setForm((current) => ({ ...current, [field]: event.target.value }))
    }
  }

  async function submit(event) {
    event.preventDefault()
    setPending(true)
    setSaved(false)
    setFailure({ message: null, fieldErrors: {} })

    try {
      const updated = await updateProfile(form)
      profile.setData(updated)
      applyUser?.(updated)
      setSaved(true)
    } catch (error) {
      setFailure(describeFailure(error))
    } finally {
      setPending(false)
    }
  }

  if (profile.isLoading) return <SkeletonList rows={2} label="Loading your profile" />
  if (profile.hasFailed) {
    return <ErrorState title="We could not load your profile" error={profile.error} onRetry={profile.reload} />
  }

  return (
    <>
      <PageHeader
        eyebrow="Profile"
        title="About you"
        lead="What you tell us here shapes how your analyses are framed — the role you are aiming at, and how far along you are."
      />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
        <section className="panel p-5 sm:p-7">
          <form onSubmit={submit} className="space-y-5">
            <TextField
              label="Full name"
              name="fullName"
              value={form.fullName}
              onChange={update('fullName')}
              error={failure.fieldErrors.fullName}
              maxLength={120}
              required
            />

            <TextField
              label="Target role"
              name="targetRole"
              value={form.targetRole}
              onChange={update('targetRole')}
              error={failure.fieldErrors.targetRole}
              hint="The job you are aiming at — “Backend Developer”, “Data Analyst”. Shown on your dashboard."
              maxLength={120}
            />

            <div>
              <label htmlFor="experienceLevel" className="field-label">
                Experience level
              </label>
              <select
                id="experienceLevel"
                name="experienceLevel"
                value={form.experienceLevel}
                onChange={update('experienceLevel')}
                aria-invalid={failure.fieldErrors.experienceLevel ? 'true' : undefined}
                className={`field mt-2 ${failure.fieldErrors.experienceLevel ? 'field-invalid' : ''}`}
              >
                <option value="">Prefer not to say</option>
                {EXPERIENCE_LEVELS.map((level) => (
                  <option key={level} value={level}>
                    {humanise(level)} — {EXPERIENCE_HINTS[level]}
                  </option>
                ))}
              </select>
              {failure.fieldErrors.experienceLevel ? (
                <p className="mt-2 text-xs text-danger-600">{failure.fieldErrors.experienceLevel}</p>
              ) : null}
            </div>

            <FormError message={failure.message} />

            <div className="flex items-center gap-3">
              <button type="submit" disabled={pending} className="btn btn-primary">
                {pending
                  ? <Loader2 size={16} className="animate-spin" aria-hidden="true" />
                  : <Save size={16} aria-hidden="true" />}
                {pending ? 'Saving…' : 'Save changes'}
              </button>

              {/* The confirmation lives next to the control that caused it, and says the
                  same word the button said — "save" then "saved", not "success". */}
              <span aria-live="polite" className="text-sm text-success-600">
                {saved ? (
                  <span className="inline-flex items-center gap-1.5">
                    <Check size={15} aria-hidden="true" />
                    Saved
                  </span>
                ) : null}
              </span>
            </div>
          </form>
        </section>

        <section aria-labelledby="account-heading">
          <div className="card p-5">
            <h2 id="account-heading" className="eyebrow">
              Account
            </h2>

            <dl className="mt-4 space-y-4 text-sm">
              <div>
                <dt className="text-xs text-ink-subtle">Email</dt>
                <dd className="mt-1 truncate text-ink">{profile.data.email}</dd>
              </div>
              <div>
                <dt className="text-xs text-ink-subtle">Member since</dt>
                <dd className="mt-1 text-ink">{formatDate(profile.data.memberSince)}</dd>
              </div>
              {profile.data.lastLoginAt ? (
                <div>
                  <dt className="text-xs text-ink-subtle">Last sign-in</dt>
                  <dd className="mt-1 text-ink">{formatDateTime(profile.data.lastLoginAt)}</dd>
                </div>
              ) : null}
            </dl>

            <p className="mt-5 border-t border-line pt-4 text-xs text-ink-muted">
              Changing your email needs verification, so it is not editable here yet.
            </p>
          </div>
        </section>
      </div>
    </>
  )
}
