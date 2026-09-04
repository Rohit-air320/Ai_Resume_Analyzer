# ResumeIQ

**Know how your resume performs before you apply.**

ResumeIQ takes a resume and a specific job posting and returns a scored, explained review:
an ATS compatibility score, a match score against that posting, the skills it found and the
ones it could not, keyword coverage, a section-by-section assessment, a skill-gap plan, and
concrete projects and topics to work on next.

It is a full-stack application — React 18 on Vite, Spring Boot 3.2 on Java 17, MySQL — built
to be read as much as run. Every non-obvious decision is written down next to the code that
depends on it, and the repository carries its own static checker that fails the build when the
documentation, the API and the UI stop agreeing with each other.

Java is where the numbers come from. The AI writes the words.

---

## What it reports

Every analysis produces twelve things: an ATS compatibility score, a job-description match
score, detected skills, missing skills, the posting's important keywords, keyword suggestions
that are honest to add, resume improvement suggestions, a skill-gap analysis, recommended
projects, recommended learning topics, a resume section analysis, and overall actionable
feedback.

Scores are reported with a band rather than a bare number, because 71 out of 100 means nothing
on its own:

| Score | Band |
| --- | --- |
| 0–39 | Needs major improvement |
| 40–59 | Needs improvement |
| 60–74 | Moderate match |
| 75–89 | Strong match |
| 90–100 | Excellent match |

## What the model does, and what it does not

This matters more than any feature, so it is the second thing in the README rather than a
footnote.

**Every score is computed in Java**, from the resume text and the posting text, by
`analysis/engine`. The same pair always scores the same, the arithmetic is inspectable, and
the numbers survive the AI provider being slow, unreachable or absent. The model is asked for
its own scores as a second opinion; the computed score always wins, and a disagreement wider
than `AI_SCORE_TOLERANCE` is logged so that a bad prompt or a bad parse shows up instead of
hiding.

**The model writes the prose** — the feedback, the improvement suggestions, the project ideas,
the learning plan. It is given the findings and asked to explain them. It is not asked what is
true.

It is also constrained on what it may say. It may not invent experience, skills or
certifications the resume does not support, it may not suggest a claim the candidate cannot
make honestly, and it may not encourage keyword stuffing. That last one is visible in the
product rather than just promised: the demo shows AWS, Docker, Kubernetes and microservices as
*missing* and never suggests adding them, because there is no honest place to put a skill you
do not have. Suggested keywords are always a subset of the missing ones, and each carries the
section it belongs in and why.

**The whole product runs with no AI credential at all.** `AI_PROVIDER=mock` swaps in an offline
writer that composes the same prose from the same findings. That is how the entire test suite
runs, and it is the default in `.env.example`.

The provider key is read by the backend only. It is never sent to the browser, never logged,
and never present in the frontend bundle — only `VITE_`-prefixed variables reach the client,
and no `VITE_` variable holds a secret. Resume files are never served back: there is no
download endpoint, no storage key in any response, and no resume text in any URL.

## Try it with nothing installed

The app has a public demo at `/demo` that renders the real results page against a fixture — no
account, no database, no API key, no request leaving the browser. It is not a separate mock-up:
`/demo` and `/analyses/:id` render the same `AnalysisReport` component, so the demo cannot drift
away from the product, and a static check proves the fixture still matches the API's response
shape in both directions.

`/` explains the product, `/system-check` reports whether the API, the database and the
provider configuration are actually reachable.

## Requirements

JDK 17 or newer, Maven 3.9 or newer, Node.js 18 or newer. MySQL is optional — the default
profile runs an in-memory H2 in MySQL-compatibility mode, so a fresh clone runs with nothing
installed and nothing configured.

## Quick start

```bash
cp .env.example .env
```

Then two terminals.

**API**

```bash
cd backend
mvn spring-boot:run
```

Serves <http://localhost:8080>, with Swagger UI at
<http://localhost:8080/swagger-ui.html>, the OpenAPI document at
<http://localhost:8080/v3/api-docs>, and the H2 console at
<http://localhost:8080/h2-console> (JDBC URL `jdbc:h2:mem:resumeiq`, user `sa`, no password).

**Frontend**

```bash
cd frontend
npm install
npm run dev
```

Serves <http://localhost:5173> and proxies `/api` to the backend, so the browser talks to one
origin in development and there is no CORS preflight in the common path.

You will land on the marketing page. `/demo` works immediately; creating an account takes an
email and a password and stores nothing else.

In the `dev` profile a blank `JWT_SECRET` makes the API mint a random signing key at startup,
which is convenient and means every restart invalidates existing tokens. Any other profile
refuses to start without one, because a shipped default secret is the same as no secret.

## Tests

```bash
cd backend  && mvn test
cd frontend && npm run lint && npm test
```

453 backend tests across 57 files, and 92 frontend tests across 17 files. The backend suite
covers the scoring engine, the extraction pipeline, the AI response reader, security rules and
every endpoint end to end; the frontend suite covers the session lifecycle, the analysis flow,
the charts' accessible tables, and the keyboard behaviour of the shell.

Neither suite needs an AI key, a MySQL server or a network connection.

### The static checker

```bash
python3 tools/verify_sources.py
```

10181 checks, and it is the unusual thing in this repository. It encodes the invariants that
neither the compiler nor ESLint can see: that every Spring Data derived query name resolves to
a real property path, that no `@Transactional` method is called by its own class, that a bean
with two constructors names one of them `@Autowired`, that the frontend only reads DTO fields
the backend actually returns, that every Tailwind token has both a light and a dark value, that
the sidebar and the route table are the same set, and that this README and `docs/api.md` still
describe the code underneath them.

Every check has been deliberately broken and confirmed to fire. That rule exists because three
of them were once found passing while checking nothing at all — a check that has never failed
is not evidence of anything.

## Running against MySQL

```sql
CREATE DATABASE resumeiq CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and a real `JWT_SECRET` in the environment. Schema
generation is Hibernate's during development; versioned migrations are listed under
[deliberate omissions](#limitations-and-deliberate-omissions) below.

## Configuration

Every setting lives in [`.env.example`](.env.example), documented inline with the reasoning for
its default — why BCrypt cost 12, why the refresh cookie is `SameSite=Lax`, why the posting
keyword list is capped. That file is the reference; this README does not restate it, because two
copies of the same thirty-six variables is one copy that will be wrong.

What matters for a first run:

| Variable | Purpose | Default |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` for in-memory H2, `mysql` for a real database | `dev` |
| `AI_PROVIDER` | `mock` for the offline writer, `anthropic` for a real provider | `mock` |
| `AI_API_KEY` | Provider key. Backend only, never sent to the browser | — |
| `JWT_SECRET` | Access-token signing key, 32+ characters. Optional in `dev`, required elsewhere | — |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL connection, `mysql` profile only | — |
| `RESUME_STORAGE_DIR` | Where uploaded files are written. Keep it outside any served directory | `./storage/resumes` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated browser origins | `http://localhost:5173` |
| `VITE_API_BASE_URL` | API base the browser calls. `/api` uses the dev proxy | `/api` |

`.env` is gitignored and `.env.example` holds placeholders only. No value anywhere in the
codebase is hardcoded to localhost outside a documented development default.

## API

Twenty-two endpoints under `/api`, all closed by default — the filter chain ends with
`anyRequest().authenticated()`, so a path with no handler answers `401` rather than `404`, and an
endpoint added later is private the moment it is written.

| Area | Endpoints |
| --- | --- |
| Health | `GET /api/health` (public) |
| Auth | `register`, `login`, `refresh`, `logout`, `me` |
| Resumes | upload, list, read, delete |
| Job descriptions | create, list, read, delete |
| Analyses | create, list, read, delete |
| Screens | `GET /api/dashboard`, `GET /api/recommendations`, `GET`/`PUT /api/profile` |

Sessions are a short-lived access token held only in browser memory plus a rotating refresh
token in an httpOnly cookie. Presenting a spent refresh token revokes the whole family, which
is why the frontend collapses React's double mount into a single bootstrap request.

Every error is the same envelope with a stable `code`, so the frontend maps codes to copy and no
client parses English. Stack traces never leave the server.

Full request and response shapes, with annotated JSON for every endpoint, are in
[`docs/api.md`](docs/api.md).

## Project layout

```
.
├── backend/                     Spring Boot API
│   └── src/main/java/com/resumeiq/
│       ├── analysis/            scoring, dashboard; engine/ computes, ai/ writes
│       ├── auth/                registration, login, rotating refresh tokens, throttling
│       ├── common/              error contract, shared domain, text utilities
│       ├── config/              typed properties, OpenAPI
│       ├── health/              liveness endpoint
│       ├── jobdescription/      posting storage and keyword extraction
│       ├── recommendation/      the recommendation feed
│       ├── resume/              upload, storage, extract/ for PDF and DOCX
│       ├── security/            JWT filter, current-user resolution, filter chain
│       ├── skill/               skill catalogue, aliases, scanning
│       └── user/                profile
├── frontend/                    React app
│   └── src/
│       ├── components/          analysis, charts, form, layout, marketing, score, state
│       ├── features/            one folder per domain: its API client and its hooks
│       ├── lib/                 api client, resource hook, score bands, formatters
│       ├── pages/               one file per route
│       └── test/                vitest setup
├── docs/                        architecture, database, API reference, screenshots
└── tools/verify_sources.py      the static checker
```

Backend packages are by feature, not by layer: a change to resumes touches one directory rather
than five. `common/` may not import a feature package, and a static check enforces it.

## Documentation

[Architecture](docs/architecture.md) is the one to read first — it explains the request path, why
the packages are shaped this way, how an analysis is produced, the one-bean-per-transaction rule,
and the frontend's conventions. [Database design](docs/database.md) covers the eleven tables,
their indexes and the decisions behind them. [API reference](docs/api.md) documents every
endpoint. [Screenshots](docs/screenshots/README.md) lists the captures the README expects.

## How it was built

Fifteen phases, each one runnable, reviewed and verified before the next began: setup, schema,
authentication, upload and extraction, posting processing, the AI service, the APIs, the React
shell, charts, the public pages, accessibility, and this documentation. Phases 1 through 11
and 14 are complete. Consolidating the test suites, container packaging and deployment
readiness are what remain, and the section below says what that means in practice.

The phase boundaries are visible in the git history and in `docs/architecture.md`, which grew a
section per phase rather than being written at the end.

## Limitations and deliberate omissions

Analysis is synchronous. One `POST /api/analyses` performs extraction, scoring and the provider
call inside the request, which takes a few seconds and is why the processing screen names the
four stages the server is performing rather than drawing a fake progress bar. It is deliberately
not idempotent: re-running the same resume and posting after an edit is a reasonable thing to
want. Moving the work to a queue needs no schema or API change, because the status column already
distinguishes stored from analysed.

Not built, and each for a reason worth stating rather than hiding:

Versioned schema migrations. Hibernate generates the schema during development, which is
appropriate while the model is still moving and inappropriate the moment there is data worth
keeping. Flyway is the intended replacement.

Container images and a compose file. The application runs from two commands today; packaging is
the next phase rather than an omission.

Rate limiting on the analysis endpoint. Login is throttled per email and per address; the
analysis endpoint is bounded only by the per-account resume and posting quotas, which is enough
for a single-tenant deployment and not enough for a public one.

Resume version comparison. The data model supports it — analyses are rows, not overwrites — but
there is no screen that puts two of them side by side, which is the single most useful thing this
product could add next.

Email delivery, so there is no password reset and no verification. Adding it means adding a
provider, a token table and a bounce policy, and none of that makes the analysis better.

## Licence

MIT. See [LICENSE](LICENSE).
