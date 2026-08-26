import { Link, useLocation, useParams } from 'react-router-dom'
import { AlertTriangle, ArrowLeft, CheckCircle2, CircleDashed, Sparkles } from 'lucide-react'
import PageHeader from '../components/layout/PageHeader.jsx'
import ScoreMeter from '../components/score/ScoreMeter.jsx'
import ScorePill from '../components/score/ScorePill.jsx'
import ErrorState from '../components/state/ErrorState.jsx'
import { SkeletonDashboard } from '../components/state/Skeleton.jsx'
import { getAnalysis } from '../features/analyses/analysisApi.js'
import { formatDateTime, humanise } from '../lib/format.js'
import { useResource } from '../lib/useResource.js'

/**
 * One analysis, in full.
 *
 * **Reads the document the create call already returned.** Arriving from the wizard, the
 * analysis comes in via router state, so the page renders with no request at all; arriving
 * from a link or a refresh, it fetches. The API guarantees both endpoints return the same
 * document, which is what makes that shortcut safe rather than a source of drift.
 *
 * **The order of this page is an argument.** The verdict and the sentence explaining it
 * come first, then the arithmetic behind the number, then the gaps, then what to do about
 * them. A results screen that opens with six dials tells the reader to work out for
 * themselves what matters; this one answers "how did I do", "why", and "what now", in that
 * order, because that is the order the questions arrive in.
 *
 * Charts, the skill-gap radar and the match rail arrive in Phase 9. Every number here is
 * already readable as text, which is the right base layer — a chart should add pattern to
 * information that is legible without it, not be the only way to read a score.
 */

const IMPORTANCE_STYLES = {
  CRITICAL: 'text-danger-600',
  IMPORTANT: 'text-warning-600',
  NICE_TO_HAVE: 'text-ink-subtle',
}

const PRIORITY_STYLES = {
  HIGH: 'text-danger-600',
  MEDIUM: 'text-warning-600',
  LOW: 'text-ink-subtle',
}

const STATUS_ICONS = {
  STRONG: CheckCircle2,
  PARTIAL: CircleDashed,
}

function Section({ title, lead, children }) {
  const id = title.toLowerCase().replace(/[^a-z]+/g, '-')

  return (
    <section aria-labelledby={id} className="panel p-5 sm:p-7">
      <h2 id={id} className="text-base font-semibold">
        {title}
      </h2>
      {lead ? <p className="mt-1.5 max-w-2xl text-sm text-ink-muted">{lead}</p> : null}
      <div className="mt-6">{children}</div>
    </section>
  )
}

function AdviceList({ items, empty }) {
  if (!items || items.length === 0) {
    return <p className="text-sm text-ink-muted">{empty}</p>
  }

  return (
    <ol className="space-y-3">
      {items.map((advice) => (
        <li key={advice.title} className="card p-4 sm:p-5">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h3 className="text-sm font-semibold text-ink">{advice.title}</h3>
            <span className={`chip ${PRIORITY_STYLES[advice.priority] ?? 'text-ink-subtle'}`}>
              {humanise(advice.priority)} priority
            </span>
          </div>

          <p className="mt-2 text-sm leading-relaxed text-ink-muted">{advice.detail}</p>

          {advice.resourceUrl ? (
            <a
              href={advice.resourceUrl}
              target="_blank"
              rel="noreferrer noopener"
              className="mt-3 inline-block text-sm font-medium text-brand-700 hover:underline"
            >
              Where to learn it
            </a>
          ) : null}
        </li>
      ))}
    </ol>
  )
}

export default function AnalysisDetail() {
  const { id } = useParams()
  const location = useLocation()
  const handed = location.state?.analysis?.id === id ? location.state.analysis : null

  const analysis = useResource(() => (handed ? Promise.resolve(handed) : getAnalysis(id)), [id])

  if (analysis.isLoading) return <SkeletonDashboard />
  if (analysis.hasFailed) {
    return (
      <ErrorState
        title="We could not load this analysis"
        error={analysis.error}
        onRetry={analysis.reload}
      />
    )
  }

  const result = analysis.data
  const target = result.target ?? {}

  return (
    <>
      <Link to="/analyses" className="btn btn-ghost mb-4 -ml-2">
        <ArrowLeft size={15} aria-hidden="true" />
        All analyses
      </Link>

      <PageHeader
        eyebrow={target.company ? `${target.jobTitle} · ${target.company}` : target.jobTitle}
        title="Analysis"
        lead={`${target.resumeLabel} · analysed ${formatDateTime(result.completedAt ?? result.createdAt)}`}
      >
        <Link to="/analyses/new" className="btn btn-secondary">
          <Sparkles size={15} aria-hidden="true" />
          Run another
        </Link>
      </PageHeader>

      {result.status === 'FAILED' ? (
        <div role="alert" className="panel p-6 sm:p-8">
          <AlertTriangle size={20} className="text-warning-500" aria-hidden="true" />
          <h2 className="mt-4 text-base font-semibold">This analysis did not finish</h2>
          <p className="mt-2 max-w-prose text-sm text-ink-muted">
            {result.failureReason ||
              'Something went wrong while scoring this resume. Nothing about your resume caused it.'}
          </p>
          <Link to="/analyses/new" className="btn btn-primary mt-5">
            Try again
          </Link>
        </div>
      ) : (
        <div className="space-y-6">
          {/* The verdict, and the sentence that justifies it. */}
          <section aria-labelledby="verdict" className="panel p-5 sm:p-7">
            <div className="flex flex-wrap items-center gap-4">
              <ScorePill score={result.overallScore} size="lg" />
              <h2 id="verdict" className="text-sm font-medium text-ink-muted">
                Overall match for this role
              </h2>
            </div>

            {result.overallFeedback ? (
              <p className="mt-5 max-w-3xl text-[0.95rem] leading-relaxed text-ink">
                {result.overallFeedback}
              </p>
            ) : null}

            <div className="mt-7 grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
              <ScoreMeter label="ATS compatibility" score={result.atsScore} />
              <ScoreMeter label="Job match" score={result.jobMatchScore} />
              <ScoreMeter label="Skills match" score={result.skillsMatchScore} />
              <ScoreMeter label="Keywords" score={result.keywordScore} />
              <ScoreMeter label="Experience" score={result.experienceScore} />
            </div>

            {result.provenance ? (
              <p className="mt-7 border-t border-line pt-4 text-xs text-ink-subtle">
                Written by {result.provenance.writtenBy}
                {result.provenance.modelWritten ? '' : ' (offline writer — the scores are unaffected)'}
                {result.provenance.processingMs ? (
                  <>
                    {' · '}
                    <span data-numeric="">{result.provenance.processingMs}</span>ms
                  </>
                ) : null}
              </p>
            ) : null}
          </section>

          {result.scoreBreakdown?.length > 0 ? (
            <Section
              title="How the score was reached"
              lead="Each line is part of the arithmetic. Nothing here is a black box — the numbers come from the comparison, not from the model."
            >
              <ul className="divide-y divide-line">
                {result.scoreBreakdown.map((reason) => (
                  <li key={reason.label} className="flex flex-wrap gap-x-6 gap-y-1 py-3 first:pt-0 last:pb-0">
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-medium text-ink">{reason.label}</p>
                      {reason.comment ? (
                        <p className="mt-1 text-xs text-ink-muted">{reason.comment}</p>
                      ) : null}
                    </div>

                    {/* outOf is 0 for a note that carries context rather than points. */}
                    {reason.outOf > 0 ? (
                      <p data-numeric="" className="text-sm text-ink-muted">
                        <span className="font-semibold text-ink">{reason.earned}</span> / {reason.outOf}
                      </p>
                    ) : null}
                  </li>
                ))}
              </ul>
            </Section>
          ) : null}

          <div className="grid gap-6 xl:grid-cols-2">
            <Section
              title="Skills you demonstrate"
              lead="Read out of your resume, with the evidence that backs each one."
            >
              {result.detectedSkills?.length > 0 ? (
                <ul className="space-y-3">
                  {result.detectedSkills.map((skill) => {
                    const Icon = STATUS_ICONS[skill.status] ?? CircleDashed

                    return (
                      <li key={`${skill.name}-${skill.status}`} className="flex gap-3">
                        <Icon
                          size={16}
                          className={`mt-0.5 shrink-0 ${skill.status === 'STRONG' ? 'text-success-600' : 'text-warning-600'}`}
                          aria-hidden="true"
                        />
                        <div className="min-w-0">
                          <p className="text-sm font-medium text-ink">
                            {skill.name}
                            <span className="ml-2 text-xs font-normal text-ink-subtle">
                              {humanise(skill.status)}
                            </span>
                          </p>
                          {skill.note ? (
                            <p className="mt-1 text-xs text-ink-muted">{skill.note}</p>
                          ) : null}
                        </div>
                      </li>
                    )
                  })}
                </ul>
              ) : (
                <p className="text-sm text-ink-muted">
                  We could not match any of the posting&apos;s skills to your resume.
                </p>
              )}
            </Section>

            <Section
              title="Skill gaps"
              lead="What the posting asks for that your resume does not show. Ordered by how much it matters."
            >
              {result.missingSkills?.length > 0 ? (
                <ul className="space-y-3">
                  {result.missingSkills.map((skill) => (
                    <li key={skill.name} className="flex gap-3">
                      <span
                        aria-hidden="true"
                        className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-danger-500"
                      />
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-ink">
                          {skill.name}
                          <span
                            className={`ml-2 text-xs font-normal ${IMPORTANCE_STYLES[skill.importance] ?? 'text-ink-subtle'}`}
                          >
                            {humanise(skill.importance)}
                          </span>
                        </p>
                        {skill.note ? <p className="mt-1 text-xs text-ink-muted">{skill.note}</p> : null}
                      </div>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-sm text-ink-muted">
                  Nothing missing — your resume covers every skill this posting names.
                </p>
              )}
            </Section>
          </div>

          <Section
            title="Keywords"
            lead="Terms an applicant tracking system is likely to look for. Every suggestion comes with the place it honestly belongs — a list of words to sprinkle in would be keyword stuffing, and that is not what this is."
          >
            <div className="grid gap-7 lg:grid-cols-2">
              <div>
                <h3 className="eyebrow">Already in your resume</h3>
                <ul className="mt-3 flex flex-wrap gap-2">
                  {result.matchingKeywords?.length > 0 ? (
                    result.matchingKeywords.map((keyword) => (
                      <li key={keyword} className="chip text-success-600">
                        {keyword}
                      </li>
                    ))
                  ) : (
                    <li className="text-sm text-ink-muted">None of the posting&apos;s key terms appear.</li>
                  )}
                </ul>

                {result.missingKeywords?.length > 0 ? (
                  <>
                    <h3 className="eyebrow mt-6">Absent</h3>
                    <ul className="mt-3 flex flex-wrap gap-2">
                      {result.missingKeywords.map((keyword) => (
                        <li key={keyword} className="chip text-ink-muted">
                          {keyword}
                        </li>
                      ))}
                    </ul>
                  </>
                ) : null}
              </div>

              <div>
                <h3 className="eyebrow">Worth adding, and where</h3>
                {result.suggestedKeywords?.length > 0 ? (
                  <ul className="mt-3 space-y-3">
                    {result.suggestedKeywords.map((suggestion) => (
                      <li key={suggestion.term}>
                        <p className="text-sm font-medium text-ink">{suggestion.term}</p>
                        <p className="mt-1 text-xs text-ink-muted">{suggestion.placement}</p>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="mt-3 text-sm text-ink-muted">
                    Nothing to add. Any remaining absent term had no honest place in your resume, so it
                    was left out rather than suggested.
                  </p>
                )}
              </div>
            </div>
          </Section>

          {result.sectionScores?.length > 0 ? (
            <Section
              title="Section by section"
              lead="In the order somebody reads your resume, not in score order."
            >
              <div className="grid gap-5 sm:grid-cols-2">
                {result.sectionScores.map((section) => (
                  <ScoreMeter
                    key={section.section}
                    label={humanise(section.section)}
                    score={section.score}
                    note={section.note}
                  />
                ))}
              </div>
            </Section>
          ) : null}

          <Section
            title="What to change"
            lead="Specific edits to the resume you uploaded. Nothing here asks you to claim anything you have not done."
          >
            <AdviceList
              items={result.improvements}
              empty="No changes suggested — this resume already reads well against the posting."
            />
          </Section>

          <div className="grid gap-6 xl:grid-cols-2">
            <Section title="Projects worth building" lead="Ways to turn a gap into evidence.">
              <AdviceList items={result.recommendedProjects} empty="No project suggestions for this run." />
            </Section>

            <Section title="What to learn next" lead="Ordered by what this role actually asks for.">
              <AdviceList
                items={result.learningRecommendations}
                empty="No learning suggestions for this run."
              />
            </Section>
          </div>
        </div>
      )}
    </>
  )
}
