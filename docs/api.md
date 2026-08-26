# API reference

The API documents itself. With the backend running:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>

Every timestamp is UTC, ISO-8601, and at **microsecond** precision — the precision the database
column keeps. That matters if you compare them: the value a `POST` returns is the value every
later `GET` of the same row returns, exactly, so a timestamp is safe to use as a cache key or an
equality check.

## Error envelope

Every failed request returns this shape, whatever the cause:

```json
{
  "code": "UNSUPPORTED_FILE_TYPE",
  "message": "Upload a PDF or DOCX file.",
  "fieldErrors": [{ "field": "file", "message": "must be a PDF or DOCX" }],
  "path": "/api/resumes/upload",
  "timestamp": "2026-08-23T09:14:02.117Z"
}
```

`fieldErrors` appears only on validation failures. `code` is one of the values below and is
stable; `message` is display text and may change. Clients switch on `code` and never on `message`.

| Code | HTTP | Meaning |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | One or more fields failed validation |
| `BAD_REQUEST` | 400 | Request could not be interpreted |
| `UNAUTHORIZED` | 401 | Missing, expired or malformed access token |
| `INVALID_CREDENTIALS` | 401 | Email and password did not match an account |
| `SESSION_EXPIRED` | 401 | Refresh token missing, expired, or already used |
| `FORBIDDEN` | 403 | Resource belongs to another user |
| `NOT_FOUND` | 404 | No such resource for this user |
| `METHOD_NOT_ALLOWED` | 405 | Wrong HTTP verb for the path |
| `CONFLICT` | 409 | Collides with existing data, e.g. duplicate email |
| `FILE_TOO_LARGE` | 413 | Upload exceeded the configured maximum |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Content type not accepted |
| `UNSUPPORTED_FILE_TYPE` | 415 | Upload was not PDF or DOCX |
| `UNREADABLE_FILE` | 422 | No text could be extracted |
| `TOO_MANY_REQUESTS` | 429 | Too many failed sign-ins; carries `Retry-After` |
| `AI_UNAVAILABLE` | 503 | Provider unreachable or timed out |
| `AI_INVALID_RESPONSE` | 502 | Provider response failed schema validation |
| `INTERNAL_ERROR` | 500 | Unexpected fault, details in the server log only |

Three of those codes are all 401, and the distinction is the point. `UNAUTHORIZED` means the access
token is missing or unusable, and the correct client response is one silent refresh.
`SESSION_EXPIRED` means the refresh token itself is finished, and the only correct move is to show
the sign-in screen. `INVALID_CREDENTIALS` is the answer to a wrong email or a wrong password —
deliberately the same code and the same wording for both, because two codes would turn the sign-in
form into a way to find out who has an account here.

## Authentication

A session is two tokens with different jobs.

The **access token** is a short-lived JWT (15 minutes by default), returned in the response body of
register, login and refresh. The client holds it in memory and sends it as
`Authorization: Bearer <token>`. It is never written to `localStorage` or `sessionStorage`: anything
readable by script is readable by injected script, and a token in storage survives the tab that
earned it.

The **refresh token** is 256 random bits, returned only as a `Set-Cookie` header and never in a
response body. Its attributes are all load-bearing:

| Attribute | Value | Why |
| --- | --- | --- |
| `HttpOnly` | always | Script cannot read it, so an XSS bug costs 15 minutes rather than 7 days |
| `SameSite` | `Lax` | A cross-site `POST` carries no cookie, which is what makes `/api/auth/refresh` safe without a CSRF token |
| `Path` | `/api/auth` | The browser does not attach a week-long credential to every upload and analysis request |
| `Secure` | true except local dev | `http://localhost` is not a secure context, so the browser would drop the cookie |
| `Domain` | absent | Host-only. A shared parent domain is how one compromised subdomain ends up holding another one's sessions |
| `Max-Age` | 7 days | Rotation means this bounds inactivity, not the life of any one token |

Every call to `/api/auth/refresh` spends the presented token and issues a new one in the same
family. Presenting a token that has already been spent revokes the **entire family** and answers
`SESSION_EXPIRED` — the server cannot tell the thief from the owner, so it ends the session and both
sign in again.

Because `SameSite=Lax` is the CSRF defence, the frontend and the API have to be same-site. The Vite
dev proxy models that in development: the browser talks to `localhost:5173` and never sees a
cross-origin request.

Failed sign-ins are throttled per email and per address. After five failures the email is locked for
15 minutes and further attempts answer `429` with a `Retry-After` header — including attempts that
present the correct password, because the throttle is consulted before the password is compared.

### `POST /api/auth/register`

```json
{ "email": "casey@example.com", "password": "at-least-8-characters", "fullName": "Casey Rivers" }
```

`201` with a session. `409 CONFLICT` if the email is taken, whatever its capitalisation. Passwords
are 8–72 characters; the upper bound is BCrypt's, which silently ignores bytes past 72, and
rejecting a longer value is more honest than truncating one.

### `POST /api/auth/login`

```json
{ "email": "casey@example.com", "password": "at-least-8-characters" }
```

`200` with a session. `401 INVALID_CREDENTIALS` for any wrong combination.
`429 TOO_MANY_REQUESTS` once the email or the address has failed too often.

### `POST /api/auth/refresh`

No body. Reads the refresh cookie, rotates it, returns a new session.
`401 SESSION_EXPIRED` when there is nothing to renew.

### `POST /api/auth/logout`

No body. Revokes the session behind the cookie and clears the cookie. Always `204`, whether or not
a session was found — a client that asked to sign out must end up signed out locally either way.

### `GET /api/auth/me`

Requires a bearer token. Returns the current account, so the UI can hydrate on page load and
confirm the token is still good.

### Session response

`register`, `login` and `refresh` all return the same body:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "user": {
    "id": "8f14e45f-ceea-467a-9f2c-9a1b3c4d5e6f",
    "email": "casey@example.com",
    "fullName": "Casey Rivers",
    "targetRole": null,
    "experienceLevel": null,
    "role": "USER",
    "memberSince": "2026-08-24T09:14:02.117Z",
    "lastLoginAt": "2026-08-24T09:14:02.117Z"
  }
}
```

`expiresInSeconds` is sent so the client can refresh a little early instead of waiting to be told
401. Decoding the token to find its expiry would work too, but a bearer token should stay opaque to
the code carrying it.

## Resumes

A resume is a stored file plus the text read out of it. Only the text is ever used again: the
analysis works from it, the list shows metadata about it, and the original bytes are written once and
never read back.

There is deliberately **no download endpoint**. Nothing in the product needs one, and adding one
would mean serving user-uploaded binaries from the API's own origin — where a crafted "PDF" that a
browser decides to render as HTML becomes stored XSS against every signed-in user. For the same
reason `storageKey` appears in no response: the names of files on the server are not a client's
business.

### `POST /api/resumes/upload`

`multipart/form-data` with one part named `file` and an optional text field `label`. A JSON body to
this path is refused as `415` by the framework before any application code runs.

The format is decided by reading the file's first bytes. The filename extension and the
`Content-Type` the browser sent are both supplied by the client, so neither is consulted — a DOCX
named `resume.pdf` and declared `application/pdf` is stored as a DOCX. A pre-2007 `.doc` is
recognised only so the refusal can be useful: "open it and save it as .docx" is actionable where
"unsupported file type" is a dead end.

Nothing the uploader types becomes part of a path. The file is stored under a server-generated UUID
sharded by year and month, so a file called `../../../etc/passwd.pdf` is stored as
`2026/08/3f9c….pdf` like everything else. The original filename is kept for display, with any
directory components stripped — some browsers send a full local path, and
`C:\Users\priya\Desktop\cv.pdf` in a screenshot tells everyone who sees it the person's name.

| Limit | Default | Environment variable |
| --- | --- | --- |
| File size | 5 MB | `MAX_UPLOAD_SIZE` |
| Minimum extracted characters | 200 | `MIN_RESUME_CHARACTERS` |
| Maximum stored characters | 40,000 | `MAX_RESUME_CHARACTERS` |
| Saved resumes per account | 25 | `MAX_RESUMES_PER_USER` |

The size limit is enforced twice, by the servlet container and again by the service. The container's
copy is the one that protects the server, because it rejects an oversized body before reading all of
it; the service's copy is there because a service that assumes its caller validated the input is
correct only by luck. Both read `MAX_UPLOAD_SIZE`, so they cannot drift apart.

The character floor catches the case that would otherwise produce a confident score from nothing: a
scanned or image-only resume parses perfectly and yields no words. The ceiling bounds what is stored
and, from Phase 6, what is sent to the model. Text above it is truncated at a line boundary rather
than refused.

`201` with the resume. `400` for an empty part, `413 FILE_TOO_LARGE` above the size limit,
`415 UNSUPPORTED_FILE_TYPE` for anything that is not a PDF or DOCX, `409 CONFLICT` once the account
is full, and `422 UNREADABLE_FILE` if the upload did not arrive intact.

### Resume response

```json
{
  "id": "8f14e45f-ceea-467a-9f2c-9a1b3c4d5e6f",
  "label": "Priya Sharma - backend",
  "originalFilename": "priya-resume.pdf",
  "contentType": "application/pdf",
  "fileSizeBytes": 48213,
  "pageCount": 2,
  "wordCount": 412,
  "status": "TEXT_EXTRACTED",
  "analysable": true,
  "createdAt": "2026-08-24T09:14:02.117Z"
}
```

| Status | Meaning |
| --- | --- |
| `UPLOADED` | Stored, text not extracted yet. Extraction is synchronous, so this is brief |
| `TEXT_EXTRACTED` | Readable. The only state an analysis may be started from |
| `EXTRACTION_FAILED` | Stored but unreadable. `extractionError` says why, in words a person can act on |

A file that stores but cannot be read still returns `201`, with `EXTRACTION_FAILED` rather than an
error. That is the deliberate choice: the bytes are already on disk by then, and more importantly the
row is what makes the failure visible. The resume appears in the list explaining why it cannot be
analysed, and deleting it is one request. A rejection would leave the person with a toast message and
no record of what went wrong.

`analysable` is derived, not stored — `TEXT_EXTRACTED` with at least one word. The client should gate
the analyse button on it instead of re-deriving the rule.

### `GET /api/resumes`

Every resume you own, newest first. **Metadata only** — the query does not select the extracted-text
column at all, so twenty resumes is twenty rows rather than a megabyte of text nobody asked for.

### `GET /api/resumes/{id}`

One resume, with `textPreview`: a short excerpt of what was actually read, so you can confirm the
right document was parsed before spending an analysis on it. `404 NOT_FOUND` if it does not exist
**or is not yours** — the two are the same answer on purpose, because "that exists, but not for you"
is itself information about another account.

### `DELETE /api/resumes/{id}`

`204`. Removes the database row and the stored file, in that order: if the file removal fails the
user has still lost sight of the resume and the leftover bytes are a housekeeping problem, whereas
reversed it would leave a resume in the list whose file no longer exists. `404` if it does not exist
or is not yours.

## Job descriptions

A job description is pasted text plus what the backend reads out of it. There is no file and no
parser to fool, so validation is short: the only thing that can be wrong with a posting is its
length.

Everything the parse returns is computed in Java, from a fixed skill catalogue, with no AI call. That
is the deliberate half of the analysis. "This posting requires Docker" is a claim this project can
defend line by line, and it keeps working with the provider down — paste a posting and you see its
required skills, its keywords and its seniority immediately, before any model is involved.

The parse is **recomputed on every read, never stored**. That is the point rather than an
inefficiency: the skill catalogue grows, and a posting saved last month should benefit from a skill
added last week without a migration or a backfill.

| Limit | Default | Environment variable |
| --- | --- | --- |
| Minimum characters | 200 | `MIN_POSTING_CHARACTERS` |
| Maximum characters | 20,000 | `MAX_POSTING_CHARACTERS` |
| Saved postings per account | 50 | `MAX_POSTINGS_PER_USER` |
| Keywords returned | 25 | `MAX_POSTING_KEYWORDS` |

The floor is a refusal and the ceiling is a truncation, which sounds inconsistent and is not. Below
200 characters somebody copied the job title and lost the body, and scoring a resume against three
lines produces a confident number that means nothing. Above 20,000 the posting is simply long — and
postings run long at the *end*, where the perks, the EEO statement and the how-to-apply live, so
cutting there keeps every requirement and drops the boilerplate. Refusing it would mean telling
somebody to edit a job description before we would look at it.

The posting quota is double the resume quota on purpose: the natural loop is one resume against many
postings.

`MAX_POSTING_KEYWORDS` is a cap on advice, not on data. Two hundred "important keywords" is not a
list anyone can act on, and presenting one as a checklist is exactly the nudge toward keyword
stuffing this product refuses to give.

### `POST /api/job-descriptions`

JSON with `title` (required), `company` (optional) and `text`.

`201` with the posting and its parse for a new description. **`200` with the existing one** if you
have pasted this text before. Re-pasting is the normal way to re-analyse an updated resume — the core
loop of this product is one posting and several versions of a resume — so a `409` there would be
technically defensible and infuriating. De-duplication is by SHA-256 of the whitespace-collapsed,
case-folded text, scoped per account: two people applying to the same job must not be able to detect
each other through a shared row.

Reuse is checked *before* the quota, so somebody sitting at fifty saved postings can still re-paste
one of those fifty and get on with their analysis.

`400` if the text is shorter than the minimum, `409 CONFLICT` once the account is full, `422` never —
there is no extraction step to fail.

### Job description response

```json
{
  "id": "b71c0d5e-3f2a-4c19-9d7e-2a5f8c1b4e60",
  "title": "Backend Engineer",
  "company": "Acme",
  "text": "We are looking for a Backend Engineer …",
  "insight": {
    "requiredSkills": [
      { "slug": "java", "name": "Java", "category": "LANGUAGE", "mentions": 4, "foundUnder": "Requirements" },
      { "slug": "spring-boot", "name": "Spring Boot", "category": "FRAMEWORK", "mentions": 2, "foundUnder": "Requirements" }
    ],
    "preferredSkills": [
      { "slug": "docker", "name": "Docker", "category": "DEVOPS", "mentions": 1, "foundUnder": "Nice to have" }
    ],
    "mentionedSkills": [],
    "keywords": [
      { "term": "microservices", "occurrences": 3, "inRequirements": true },
      { "term": "code review", "occurrences": 2, "inRequirements": false }
    ],
    "experience": {
      "minYears": 3,
      "maxYears": 5,
      "level": "MID",
      "evidence": "3-5 years"
    },
    "wordCount": 412,
    "structured": true,
    "sectionsFound": ["PREFERRED", "REQUIREMENTS", "RESPONSIBILITIES"]
  },
  "createdAt": "2026-08-24T09:14:02.117Z"
}
```

Skills arrive in three lists rather than one list with an importance field, because "Required" and
"Nice to have" are different headings on the page and a client that has to group an array by an enum
before it can render it will grow a helper for doing so in every component.

`foundUnder` is the heading the skill was found under, in the poster's own words. It is the most
convincing thing the UI can display: "Docker — found under: Nice to have" is an argument, where
"Docker — preferred" is an assertion.

`mentionedSkills` is kept separate and last. A technology named once in passing is not a requirement,
and presenting it as one sends people off to learn things nobody asked for.

`experience` is **absent when the posting never said**, which is not the same as saying no experience
is needed. `0 years required` would be a claim the posting never made. Note that no number of years
ever produces `LEAD`: that level is about leading people, and only a title can say it.

`structured` is `false` when the posting had no headings this parser recognised — a wall of pasted
text, or formatting lost in the clipboard. In that case the whole posting is read as requirements,
because someone who pastes a wall of text still means "this is what the job needs". The importance
labels are then a reasonable default rather than something the text actually said, and the UI should
say so. `sectionsFound` is always computed from the posting's *own* headings, never from that
promotion, so it never claims a heading the user could scroll up and fail to find.

### `GET /api/job-descriptions`

Every posting you own, newest first. **Metadata only** — neither the text nor the parse is included,
so listing fifty postings does not run the parser fifty times to render a page that shows none of its
output.

### `GET /api/job-descriptions/{id}`

One posting, with its text and a fresh parse. The full text is returned here, unlike a resume, where
only an excerpt is. The difference is not an oversight: a resume's original file is a binary this API
never serves, whereas a job description is plain text the user pasted themselves, and handing it back
is the feature — reading an analysis from six weeks ago is worth little if you cannot see the posting
it was measured against.

`404 NOT_FOUND` if it does not exist or is not yours.

### `DELETE /api/job-descriptions/{id}`

`204`. Nothing on disk to clean up. `404` if it does not exist or is not yours.

## Analyses

An analysis is one resume measured against one posting at one moment, and unlike a posting's parse it
is **stored rather than recomputed**. The two rules look inconsistent side by side and they answer
different questions. A posting's parse answers "what does this job need?", which should improve as the
catalogue grows. An analysis answers "how did my resume score?", and a number that quietly changed
because the weights changed would make the history chart a lie — the line would move for reasons the
user never acted on. So the parse is always fresh and the score is always the score that was reported.

The scores are computed in Java from the extracted text and the parsed posting. The model, when one is
configured, writes the prose. That split is the whole architecture of the feature: a provider outage
degrades the wording and never the numbers, so `POST /api/analyses` has no `503` — the request
succeeds, the offline writer produces the advice, and `provenance.modelWritten` is `false` so the UI
can say where the words came from.

Analysis is **synchronous**. There is no job id, no queue and nothing to poll: the request takes a few
seconds and returns the finished document. The processing screen in the UI is a real wait rather than a
progress bar over an empty state, and the alternative — a `202` plus a status endpoint — buys
scalability this product does not need at the cost of a state machine every client has to implement.

### `POST /api/analyses`

JSON with `resumeId` and `jobDescriptionId`, both required. `201` with the complete analysis below.

**Not idempotent, on purpose.** Analysing the same pair twice creates two rows, because re-running
after an edit is how somebody checks whether the edit helped, and the second number next to the first
*is* the product. A `409` here would refuse the main loop.

`400` if either id is missing or is not a UUID. `404` if either document does not exist or is not
yours — checked before anything is read, so a resume belonging to somebody else is never opened, let
alone scored. `422 UNREADABLE_FILE` if the resume has no extracted text: a scanned PDF uploads
successfully and scores nothing, and a number computed from an empty string would measure the
extraction while reading as a verdict on the CV. Nothing is written on a `422`, so a refused request
leaves no point on the history chart.

### Analysis response

```json
{
  "id": "c4f0a2b8-6d31-4e57-8a19-7b2e5f0c9d43",
  "status": "COMPLETED",
  "target": {
    "resumeId": "9f3c1a77-2e84-4b0d-9c56-1a7e4f8b2d31",
    "resumeLabel": "Backend CV",
    "jobDescriptionId": "b71c0d5e-3f2a-4c19-9d7e-2a5f8c1b4e60",
    "jobTitle": "Backend Engineer",
    "company": "Acme"
  },
  "overallScore": 78,
  "atsScore": 84,
  "jobMatchScore": 81,
  "skillsMatchScore": 75,
  "keywordScore": 68,
  "experienceScore": 90,
  "scoreBreakdown": [
    { "label": "Required skills", "earned": 22, "outOf": 30, "comment": "6 of 8 named in the posting" },
    { "label": "Quantified bullets", "earned": 8, "outOf": 10, "comment": "7 of 9 bullets carry a number" }
  ],
  "overallFeedback": "A strong match on the backend stack. The gap is deployment …",
  "detectedSkills": [
    { "name": "Java", "slug": "java", "status": "STRONG", "importance": "CRITICAL", "note": "Built 14 REST services in Java and Spring Boot …" }
  ],
  "missingSkills": [
    { "name": "Docker", "slug": "docker", "status": "MISSING", "importance": "NICE_TO_HAVE", "note": "Asked for under: Nice to have" }
  ],
  "matchingKeywords": ["code review", "microservices"],
  "missingKeywords": ["ci/cd", "observability"],
  "suggestedKeywords": [
    { "term": "CI/CD", "placement": "Experience — the settlement module bullet" }
  ],
  "sectionScores": [
    { "section": "CONTACT", "score": 100, "note": "Email, phone and a GitHub link" },
    { "section": "EXPERIENCE", "score": 82, "note": "Strong bullets; two lack a metric" }
  ],
  "improvements": [
    { "title": "Name your deployment tooling", "detail": "…", "priority": "HIGH", "resourceUrl": null }
  ],
  "recommendedProjects": [
    { "title": "Containerise the Ledger Reconciler", "detail": "…", "priority": "MEDIUM", "resourceUrl": null }
  ],
  "learningRecommendations": [
    { "title": "Docker fundamentals", "detail": "…", "priority": "HIGH", "resourceUrl": "https://…" }
  ],
  "provenance": {
    "writtenBy": "Structural analysis (no model)",
    "modelWritten": false,
    "analyzerVersion": "1.0.0",
    "processingMs": 412
  },
  "createdAt": "2026-08-25T09:14:02.117Z",
  "completedAt": "2026-08-25T09:14:02.529Z"
}
```

The field names are the spec's field names, and they are the same words from the prompt that asks the
model for them, through the columns they land in, to the chart that reads them. Renaming a concept at a
layer boundary is how a codebase ends up with three words for one idea and a mapping class to translate
between them.

`GET /api/analyses/{id}` returns **this exact document**, character for character. The create response
is not a rendering of the outcome that just ran; it is a read of the row that was written, through the
same mapper. A client can treat the two interchangeably, and a mapping bug shows up in both rather than
hiding in whichever one has fewer tests.

Six scores, because "your resume scores 78" is not advice. `atsScore` is about the document — parseable
sections, real headings, no tables — and `jobMatchScore` is about the fit, and those two move
independently: a beautifully formatted resume for the wrong job scores high on one and low on the
other, and only splitting them says which. `scoreBreakdown` is the arithmetic, read from stored notes
rather than recomputed, so a three-month-old analysis explains itself with the rules it was scored
under. A note with `outOf: 0` carries context rather than points.

| Range | Band |
| --- | --- |
| 0–39 | Needs major improvement |
| 40–59 | Needs improvement |
| 60–74 | Moderate match |
| 75–89 | Strong match |
| 90–100 | Excellent match |

`detectedSkills` and `missingSkills` are one table filtered two ways, each ordered by importance so a
critical gap is never the ninth row. `slug` is `null` for a skill the catalogue has not learned yet —
the honest answer, rather than a slug invented on the way out — and `note` is the evidence: the line in
the resume that supports the verdict, or the heading the posting asked under. That column is what keeps
the advice checkable. "Strengthen your Spring Boot bullet" is only legitimate if there is a Spring Boot
bullet, and the note says which one.

Every entry in `suggestedKeywords` carries a `placement`, and that is a hard rule rather than a nicety:
a term with no honest answer to "where would this go in your resume?" is dropped before it reaches a
column. A bare list of keywords to add is a keyword stuffing tool, and this product refuses to be one.

`provenance` is surfaced deliberately. A user reading advice should know whether a model read their
bullet points or whether the suggestions were derived from the structural findings, because the two
deserve different amounts of trust. `writtenBy` is a model name or a description of the offline writer,
never a key.

`failureReason` is **absent** on a completed analysis rather than `null`, like every other empty field —
the API omits nulls throughout. It is present, and safe to display, on a `FAILED` row.

The raw model response is never returned, and on a successful run it is never even stored. The parts
worth keeping are already rows; the parts that failed validation are the parts we decided not to stand
behind.

### `GET /api/analyses`

Your history, newest first. **Scores and labels only** — `overallScore`, `atsScore`, `jobMatchScore`,
the job title, the company and the resume label. The skills, keywords, section scores and advice come
with a single analysis; including them here would mean four child collections fetched per row to render
a table of numbers. This is enforced by the query rather than by filtering on the way out: the list
reads an interface projection that has no accessor for those columns at all.

### `GET /api/analyses/{id}`

One analysis, in full. `404 NOT_FOUND` if it does not exist or is not yours — never `403`, which would
confirm that the row exists.

### `DELETE /api/analyses/{id}`

`204`. The skills, keywords, section scores and recommendations go with it, since none of them has any
life outside their analysis. The resume and the posting are untouched and can be analysed again.
`404` if it does not exist or is not yours, and in that case nothing is deleted — the owner is in the
`delete` statement as well as in the lookup, because a delete that answers `404` and removes the row
anyway is the worst outcome available.

## Dashboard

### `GET /api/dashboard`

One request that fills one screen.

```json
{
  "counts": { "analyses": 7, "resumes": 2, "jobDescriptions": 5 },
  "scores": { "average": 74, "best": 86, "latest": 78 },
  "scoreHistory": [
    { "recordedAt": "2026-08-19T11:02:44.000Z", "overall": 61, "ats": 70, "jobMatch": 58 }
  ],
  "recentAnalyses": [],
  "topSkillGaps": [ { "skill": "Docker", "occurrences": 4 } ],
  "targetRole": "Backend Engineer"
}
```

This endpoint is shaped like a screen rather than like a resource, which is a deliberate exception to
how the rest of this API is organised. The alternative is five round trips — counts, averages, a trend,
a recent list and a gap aggregation — on the page a user sees first and most often. Five REST-pure
requests to render one view is a purity nobody logging in is served by.

`scores` is the part worth reading closely: each of the three fields is **omitted when there is nothing
to report**. SQL's `avg()` over no rows is null, the service passes that null through and the mapper
leaves it out, so a client can tell "nothing scored yet" from "scored zero". Defaulting it would greet a
brand-new account with a chart reporting an overall score of 0, which reads as a judgement of a resume
nobody has looked at.

`scoreHistory` is the last thirty analyses in ascending time order, ready to plot left to right.
`recentAnalyses` is the five newest, in the same shape as a row of `GET /api/analyses`. `topSkillGaps`
is the six skills missed most often across the whole account, with a count — the one thing no single
analysis can tell you, and the number that turns "you are missing Docker" into "you have been missing
Docker in four applications". It is a `group by` over one table, which is the argument for a relational
store here rather than analyses stored as documents.

`targetRole` is echoed from the profile so the empty state can be specific.

## Recommendations

### `GET /api/recommendations`

Every piece of advice across the account, newest first, capped at 100. Optional
`?type=IMPROVEMENT|LEARNING|PROJECT|KEYWORD` narrows it; a value outside those four is a `400` rather
than an empty list, because an empty list would let a client's typo look like "you have no learning
topics".

Each item carries the `analysisId` and the `jobTitle` it came from. That context is the reason this is a
joined projection rather than a list of recommendation rows: "learn Docker" means something different
under one job title than under another, and a feed that pooled advice from six applications without
saying which was which would be advice about nothing in particular.

These rows have no owner column of their own — a recommendation's owner is its analysis's owner — so
the ownership filter here is a join two levels deep. It is the endpoint where a missing filter would be
least visible, and the one place the privacy tests check a count as well as a list.

## Profile

### `GET /api/profile`

The signed-in user: `id`, `email`, `fullName`, `targetRole`, `experienceLevel`, `role`, `memberSince`
and `lastLoginAt`. The two optional fields are omitted until they are set.

### `PUT /api/profile`

`fullName` (required, ≤120 characters), `targetRole` (optional, ≤120) and `experienceLevel` (optional,
one of `ENTRY, JUNIOR, MID, SENIOR, LEAD`). `200` with the updated profile.

Note what the request cannot say: there is no email, no password and no role. Each of those is a
different operation with a different guard — changing an email needs re-verification, changing a
password needs the old one, and changing a role is not something a user does to themselves — and
folding them into one "update profile" request is how an endpoint grows a nullable field that quietly
grants an account administrator rights. Unknown properties are ignored, so sending `"role": "ADMIN"`
here is not refused, it simply has nowhere to land.

There is no id in either path. The id is the token, so there is nothing to tamper with and no ownership
check to forget.

`PUT` **replaces**: a field left out is cleared, not preserved. A record cannot distinguish "leave it
alone" from "I no longer have one" — both arrive as null — and replacement is the honest reading of the
two. A `PATCH` that could tell them apart would need a wrapper type per field, and this endpoint is
three fields wide.

An over-long name is a `400` rather than a truncation. Silently shortening somebody's own name is worse
than telling them it is too long: they would find out from a rendered page later, if at all.

## Endpoints

| Method | Path | Auth | Phase |
| --- | --- | --- | --- |
| GET | `/api/health` | public | 1 |
| POST | `/api/auth/register` | public | 3 |
| POST | `/api/auth/login` | public | 3 |
| POST | `/api/auth/refresh` | refresh cookie | 3 |
| POST | `/api/auth/logout` | refresh cookie | 3 |
| GET | `/api/auth/me` | bearer | 3 |
| POST | `/api/resumes/upload` | bearer | 4 |
| GET | `/api/resumes` | bearer | 4 |
| GET | `/api/resumes/{id}` | bearer | 4 |
| DELETE | `/api/resumes/{id}` | bearer | 4 |
| POST | `/api/job-descriptions` | bearer | 5 |
| GET | `/api/job-descriptions` | bearer | 5 |
| GET | `/api/job-descriptions/{id}` | bearer | 5 |
| DELETE | `/api/job-descriptions/{id}` | bearer | 5 |
| POST | `/api/analyses` | bearer | 7 |
| GET | `/api/analyses` | bearer | 7 |
| GET | `/api/analyses/{id}` | bearer | 7 |
| DELETE | `/api/analyses/{id}` | bearer | 7 |
| GET | `/api/dashboard` | bearer | 7 |
| GET | `/api/recommendations` | bearer | 7 |
| GET | `/api/profile` | bearer | 7 |
| PUT | `/api/profile` | bearer | 7 |

Everything not listed as public is closed by default: the filter chain ends with
`anyRequest().authenticated()`, so a path with no handler yet answers `401` rather than `404`, and
an endpoint added in a later phase is private the moment it is written.
