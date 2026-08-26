/**
 * The sample analysis behind the landing page and the demo.
 *
 * **Why a fixture and not a seeded account.** The spec asks for a demo somebody can read
 * without signing up, and the honest way to do that is to ship one document rather than to
 * open a real endpoint to anonymous callers. No request leaves the browser for this page, so
 * there is no anonymous read path to secure, no rate limit to think about, and nothing about
 * a real user's resume can ever appear here by accident.
 *
 * **It is shaped exactly like `AnalysisResponse`**, down to the sort order the API applies:
 * skills by importance then label, keywords sorted, sections in document order, scored
 * breakdown lines before context lines. That is what lets the demo render through the same
 * `AnalysisReport` the signed-in results page uses — the demo cannot drift from the product,
 * because it *is* the product with a different data source. `tools/verify_sources.py` checks
 * every key here against the Java record, so a renamed field breaks the build rather than
 * quietly emptying a section of the demo.
 *
 * **The content is the spec's demo profile** — a Software Developer at 84 ATS and 81 job
 * match, with Java, Spring Boot, React and MySQL shown and Docker and AWS missing.
 *
 * One deliberate detail: `missingKeywords` lists AWS, Docker and Kubernetes, and
 * `suggestedKeywords` does not. A term only becomes a suggestion when there is somewhere
 * truthful to put it, so the demo demonstrates the product refusing to pad a resume with
 * words the person cannot back up. That refusal is the most distinctive thing about this
 * tool, and a demo that hid it would be selling the wrong product.
 */
export const DEMO_ANALYSIS = {
  id: '00000000-0000-4000-8000-0000000000de',
  status: 'COMPLETED',
  target: {
    resumeId: '00000000-0000-4000-8000-0000000000c1',
    resumeLabel: 'Sample resume.pdf',
    jobDescriptionId: '00000000-0000-4000-8000-0000000000j1',
    jobTitle: 'Software Developer',
    company: 'Northwind Labs',
  },

  overallScore: 82,
  atsScore: 84,
  jobMatchScore: 81,
  skillsMatchScore: 78,
  keywordScore: 76,
  experienceScore: 80,

  // Scored lines first, then context lines — and the earned column sums to the overall
  // score, because a breakdown that does not add up is worse than no breakdown.
  scoreBreakdown: [
    {
      label: 'Experience relevance',
      earned: 21,
      outOf: 25,
      comment: 'Two backend roles, both describing outcomes. One is missing any measure of scale.',
    },
    {
      label: 'Formatting and parsing',
      earned: 14,
      outOf: 15,
      comment: 'Single column, standard headings, no tables or text boxes. Parses cleanly.',
    },
    {
      label: 'Keyword coverage',
      earned: 15,
      outOf: 20,
      comment: 'Six of the twelve terms this posting leans on appear in the resume.',
    },
    {
      label: 'Preferred skills',
      earned: 8,
      outOf: 10,
      comment: 'React and unit testing are both demonstrated in project work.',
    },
    {
      label: 'Required skills',
      earned: 24,
      outOf: 30,
      comment: 'Four of six required skills are evidenced. Docker and AWS are never mentioned.',
    },
    {
      label: 'Sample data',
      earned: 0,
      outOf: 0,
      comment:
        'This analysis is a fixture, not a run against a real resume. Nothing here was written by a model.',
    },
  ],

  overallFeedback:
    'This is a strong application for a backend-leaning Software Developer role. Java and Spring Boot are evidenced properly — versions named, two roles and three projects behind them — and the React work is enough to cover the front-end half of the posting. Two things hold the score down. The posting treats containerised deployment as a day-one requirement and the resume never says Docker, and the summary spends three lines on adjectives where it could name the stack and the domain. Fix the summary this afternoon; the container gap is a weekend of work that turns into a line you can defend.',

  detectedSkills: [
    {
      name: 'Java 17',
      slug: 'java',
      status: 'STRONG',
      importance: 'CRITICAL',
      note: 'Named with its version in both roles, and three projects are built on it.',
    },
    {
      name: 'Spring Boot',
      slug: 'spring-boot',
      status: 'STRONG',
      importance: 'CRITICAL',
      note: 'REST APIs in both roles, plus Spring Security in the payments service.',
    },
    {
      name: 'MySQL',
      slug: 'mysql',
      status: 'PARTIAL',
      importance: 'IMPORTANT',
      note: 'Appears in a stack list. No schema design, indexing or query work is described.',
    },
    {
      name: 'React',
      slug: 'react',
      status: 'STRONG',
      importance: 'IMPORTANT',
      note: 'Hooks and Router across two projects, one of them with component tests.',
    },
    {
      name: 'Git',
      slug: 'git',
      status: 'STRONG',
      importance: 'NICE_TO_HAVE',
      note: 'Used throughout, with code review mentioned in the current role.',
    },
  ],

  missingSkills: [
    {
      name: 'Docker',
      slug: 'docker',
      status: 'MISSING',
      importance: 'CRITICAL',
      note: 'The posting asks for containerised deployment on day one. The resume never mentions it.',
    },
    {
      name: 'AWS',
      slug: 'aws',
      status: 'MISSING',
      importance: 'IMPORTANT',
      note: 'EC2 and S3 familiarity is listed as preferred. No cloud provider appears anywhere.',
    },
  ],

  matchingKeywords: ['Java', 'MySQL', 'REST API', 'React', 'Spring Boot', 'unit testing'],
  missingKeywords: ['AWS', 'CI/CD', 'Docker', 'Kubernetes', 'Linux', 'microservices'],

  // Two of the six absent terms have an honest home in this resume. The other four do
  // not, so they are reported as absent and never suggested.
  suggestedKeywords: [
    {
      term: 'CI/CD',
      placement:
        'The payments project already describes a GitHub Actions pipeline that runs tests on every push — that is CI/CD, so name it in that bullet.',
    },
    {
      term: 'Linux',
      placement:
        'You deployed the booking app to an Ubuntu server. Say Linux in the projects section where you describe that deployment.',
    },
  ],

  sectionScores: [
    { section: 'CONTACT', score: 92, note: 'Email, phone and GitHub are all present and parseable.' },
    {
      section: 'SUMMARY',
      score: 58,
      note: 'Three lines of adjectives. Name the stack, the domain and the years instead.',
    },
    {
      section: 'SKILLS',
      score: 76,
      note: 'Grouped sensibly, but the container and cloud rows this posting wants are absent.',
    },
    {
      section: 'EXPERIENCE',
      score: 80,
      note: 'Both roles lead with outcomes. The earlier one has no figure to anchor them.',
    },
    { section: 'PROJECTS', score: 85, note: 'Three projects, each with a stack and a working link.' },
    { section: 'EDUCATION', score: 88, note: 'Degree, institution and year. Nothing missing.' },
    {
      section: 'CERTIFICATIONS',
      score: 40,
      note: 'Empty. This posting does not ask for any, so it costs almost nothing here.',
    },
    {
      section: 'FORMATTING',
      score: 90,
      note: 'One column, standard headings, no images. An ATS will read this correctly.',
    },
  ],

  improvements: [
    {
      title: 'Replace the summary with one specific sentence',
      detail:
        'It currently reads "passionate, hard-working developer seeking opportunities". Say what you build and in what: "Backend developer, two years on Java 17 and Spring Boot REST services, currently on a payments team." Same length, and it now matches the first line of the posting.',
      priority: 'HIGH',
      resourceUrl: null,
    },
    {
      title: 'Put a number on the payments service',
      detail:
        'The bullet says you "built and maintained payment APIs". You know the request volume, the number of endpoints or the latency you held. One figure makes the claim checkable, and reviewers read checkable claims as true.',
      priority: 'HIGH',
      resourceUrl: null,
    },
    {
      title: 'Name the CI pipeline you already have',
      detail:
        'Your GitHub Actions workflow runs the test suite on every push. That belongs in the project bullet — it is the posting’s "CI/CD" requirement, already met and simply unsaid.',
      priority: 'MEDIUM',
      resourceUrl: null,
    },
    {
      title: 'Move the skills block above education',
      detail:
        'A reviewer scanning for Java and Spring Boot currently finds them in the lower third of page one. Nothing is added or removed by this change — it just puts the match where the eye already is.',
      priority: 'MEDIUM',
      resourceUrl: null,
    },
  ],

  recommendedProjects: [
    {
      title: 'Containerise the payments API you already wrote',
      detail:
        'A Dockerfile, a compose file with MySQL beside it, and a paragraph in the README. It is an evening of work on a codebase you know, and it turns the biggest gap in this comparison into a line you can talk about in an interview.',
      priority: 'HIGH',
      resourceUrl: null,
    },
    {
      title: 'Deploy one existing project to AWS',
      detail:
        'EC2 for the app, S3 for the uploads, and an IAM user scoped to just that bucket. The posting asks for familiarity, not architecture, and one deployment you performed yourself is enough to answer honestly.',
      priority: 'MEDIUM',
      resourceUrl: null,
    },
  ],

  learningRecommendations: [
    {
      title: 'Docker fundamentals: images, layers, compose',
      detail:
        'Enough to write your own Dockerfile and explain what each line does. Stop before Kubernetes — this posting does not ask for it, and claiming it would be the kind of stretch that falls apart in a technical interview.',
      priority: 'HIGH',
      resourceUrl: 'https://docs.docker.com/get-started/',
    },
    {
      title: 'The three AWS services this posting names',
      detail:
        'EC2, S3 and IAM, in that order. The free tier covers everything you need to have actually used them rather than read about them.',
      priority: 'MEDIUM',
      resourceUrl: 'https://aws.amazon.com/getting-started/',
    },
  ],

  provenance: {
    writtenBy: 'Sample data — no model was called',
    modelWritten: false,
    analyzerVersion: 'demo',
    processingMs: null,
  },

  failureReason: null,
  createdAt: '2026-08-24T09:12:00Z',
  completedAt: '2026-08-24T09:12:07Z',
}
