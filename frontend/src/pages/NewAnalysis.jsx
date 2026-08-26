import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft, ArrowRight, Sparkles } from 'lucide-react'
import ProcessingPanel from '../components/analysis/ProcessingPanel.jsx'
import Stepper from '../components/analysis/Stepper.jsx'
import FormError from '../components/form/FormError.jsx'
import PageHeader from '../components/layout/PageHeader.jsx'
import PostingPicker from '../features/jobs/PostingPicker.jsx'
import ResumePicker from '../features/resumes/ResumePicker.jsx'
import { runAnalysis } from '../features/analyses/analysisApi.js'
import { describeFailure } from '../lib/formErrors.js'

/**
 * The three-step flow that produces an analysis.
 *
 * **Why the step lives in React state and the result lives in the URL.** Half-finished
 * wizard state is not worth a bookmark — nobody wants to share a link to "step two with
 * nothing chosen" — but a score is something people refresh, bookmark and come back to.
 * So the wizard is ephemeral and the outcome gets its own address at `/analyses/:id`,
 * which also means the back button after an analysis lands on the flow rather than
 * re-submitting it.
 *
 * **Why the run is not undoable and that is fine.** The server deliberately does not
 * dedupe: running the same pair twice is a legitimate thing to want after editing a
 * resume, so this screen does not try to be clever about it either. It does disable the
 * button while a run is in flight, because a double click is the one case where a second
 * analysis is definitely not what anyone meant.
 */

const STEPS = ['Resume', 'Job description', 'Run']

export default function NewAnalysis() {
  const navigate = useNavigate()
  const [step, setStep] = useState(1)
  const [resume, setResume] = useState(null)
  const [posting, setPosting] = useState(null)
  const [running, setRunning] = useState(false)
  const [failure, setFailure] = useState({ message: null, fieldErrors: {} })

  async function run() {
    setRunning(true)
    setFailure({ message: null, fieldErrors: {} })

    try {
      const analysis = await runAnalysis({ resumeId: resume.id, jobDescriptionId: posting.id })
      // The create response is the same document GET returns, so the results page can
      // render immediately and skip a round trip it would otherwise make on mount.
      navigate(`/analyses/${analysis.id}`, { replace: true, state: { analysis } })
    } catch (error) {
      setFailure(describeFailure(error))
      setRunning(false)
    }
  }

  if (running) {
    return (
      <>
        <PageHeader eyebrow="New analysis" title="Working on it" />
        <ProcessingPanel
          resumeLabel={resume?.label || resume?.originalFilename}
          jobTitle={posting?.title}
        />
      </>
    )
  }

  return (
    <>
      <PageHeader
        eyebrow="New analysis"
        title="Score a resume against a job"
        lead="Pick the resume you would actually send, and paste the posting you are actually applying to. The closer both are to the real thing, the more the numbers are worth."
      />

      <Stepper steps={STEPS} current={step} onGoTo={setStep} />

      <section className="panel p-5 sm:p-7">
        {step === 1 ? (
          <>
            <h2 className="text-base font-semibold">Which resume?</h2>
            <p className="mt-1.5 text-sm text-ink-muted">
              Choose one you have uploaded, or add a new version.
            </p>
            <div className="mt-6">
              <ResumePicker selectedId={resume?.id} onSelect={setResume} />
            </div>
          </>
        ) : null}

        {step === 2 ? (
          <>
            <h2 className="text-base font-semibold">Which job?</h2>
            <p className="mt-1.5 text-sm text-ink-muted">
              Saved postings stay in your library, so you can re-score against them later.
            </p>
            <div className="mt-6">
              <PostingPicker selectedId={posting?.id} onSelect={setPosting} />
            </div>
          </>
        ) : null}

        {step === 3 ? (
          <>
            <h2 className="text-base font-semibold">Ready to analyse</h2>
            <p className="mt-1.5 text-sm text-ink-muted">
              This takes a few seconds. You will get scores, the skills the posting asks for,
              and specific things to change.
            </p>

            <dl className="mt-6 grid gap-4 sm:grid-cols-2">
              <div className="card p-4">
                <dt className="eyebrow">Resume</dt>
                <dd className="mt-2 truncate text-sm font-medium text-ink">
                  {resume?.label || resume?.originalFilename}
                </dd>
              </div>
              <div className="card p-4">
                <dt className="eyebrow">Job description</dt>
                <dd className="mt-2 truncate text-sm font-medium text-ink">
                  {posting?.title}
                  {posting?.company ? (
                    <span className="font-normal text-ink-muted"> · {posting.company}</span>
                  ) : null}
                </dd>
              </div>
            </dl>

            <div className="mt-6">
              <FormError message={failure.message} />
            </div>
          </>
        ) : null}
      </section>

      <nav className="mt-6 flex items-center justify-between gap-3">
        <button
          type="button"
          onClick={() => setStep((current) => current - 1)}
          disabled={step === 1}
          className="btn btn-ghost"
        >
          <ArrowLeft size={15} aria-hidden="true" />
          Back
        </button>

        {step < 3 ? (
          <button
            type="button"
            onClick={() => setStep((current) => current + 1)}
            disabled={step === 1 ? !resume : !posting}
            className="btn btn-primary"
          >
            Continue
            <ArrowRight size={15} aria-hidden="true" />
          </button>
        ) : (
          <button type="button" onClick={run} disabled={!resume || !posting} className="btn btn-primary">
            <Sparkles size={15} aria-hidden="true" />
            Analyse my resume
          </button>
        )}
      </nav>
    </>
  )
}
