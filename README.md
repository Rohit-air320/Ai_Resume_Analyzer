# ResumeIQ

Know how your resume performs before you apply.

ResumeIQ analyses a resume against a specific job description and returns an ATS
compatibility score, a job match score, detected and missing skills, keyword coverage, a
section-by-section review, a skill-gap plan, and concrete projects and topics to work on.

The analysis runs entirely on the server. The AI provider key never reaches the browser.

---

## Status

Built in phases, each one runnable and tested on its own.

| Phase | Scope | State |
| --- | --- | --- |
| 1 | Project setup, error contract, design tokens, health check | ✅ Complete |
| 2 | Database schema and JPA entities | ⏳ Next |
| 3 | JWT authentication and security | ⏳ |
| 4 | Resume upload and PDF/DOCX text extraction | ⏳ |
| 5 | Job description management | ⏳ |
| 6 | AI analysis service | ⏳ |
| 7 | Analysis, dashboard and recommendation APIs | ⏳ |
| 8 | React dashboard and core flows | ⏳ |
| 9 | Charts and skill-gap visualisation | ⏳ |
| 10 | Landing page and demo mode | ⏳ |
| 11 | Responsive, dark mode, accessibility | ⏳ |
| 12 | Test suites | ⏳ |
| 13 | Docker | ⏳ |
| 14 | Documentation | ⏳ |
| 15 | Deployment readiness | ⏳ |

## Tech stack

**Backend** — Java 17, Spring Boot 3.2, Spring Web, Spring Data JPA, Spring Security with
JWT, Bean Validation, PDFBox and Apache POI for document parsing, springdoc OpenAPI.

**Frontend** — React 18, Vite, Tailwind CSS, React Router, axios, Recharts, lucide-react.

**Database** — MySQL 8 in production; H2 in MySQL-compatibility mode for local development,
so the project runs with nothing installed.

## Requirements

- JDK 17 or newer
- Maven 3.9 or newer
- Node.js 18 or newer

MySQL is optional. The default `dev` profile uses in-memory H2.

## Running locally

Two terminals.

**API**

```bash
cd backend
mvn spring-boot:run
```

Serves <http://localhost:8080>. Swagger UI at <http://localhost:8080/swagger-ui.html>,
H2 console at <http://localhost:8080/h2-console> (JDBC URL `jdbc:h2:mem:resumeiq`, user
`sa`, no password).

**Frontend**

```bash
cd frontend
npm install
npm run dev
```

Serves <http://localhost:5173> and proxies `/api` to the backend, so there is no CORS
preflight in the common path during development.

Open the app and the setup page reports whether the API is reachable.

### Tests

```bash
cd backend && mvn test
cd frontend && npm test
```

### Running against MySQL

```sql
CREATE DATABASE resumeiq CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

with `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` set in the environment.

## Environment variables

Copy `.env.example` to `.env` and fill it in. Nothing secret is committed, and no value is
hardcoded to localhost.

| Variable | Used for | Default |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` (H2) or `mysql` | `dev` |
| `SERVER_PORT` | API port | `8080` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL connection | — |
| `JWT_SECRET` | Token signing key, 32+ characters | — |
| `JWT_EXPIRATION_MINUTES` | Token lifetime | `120` |
| `AI_PROVIDER` | `anthropic` or `mock` | `mock` |
| `AI_API_KEY` | Provider key, backend only | — |
| `AI_MODEL` | Model identifier | see `.env.example` |
| `MAX_UPLOAD_SIZE` | Largest accepted resume | `5MB` |
| `CORS_ALLOWED_ORIGINS` | Allowed browser origins | `http://localhost:5173` |
| `VITE_API_BASE_URL` | API base the browser calls | `/api` |

## Project structure

```
.
├── backend/          Spring Boot API
│   └── src/main/java/com/resumeiq/
│       ├── config/   typed properties, CORS, OpenAPI
│       ├── common/   shared error contract
│       └── health/   liveness endpoint
├── frontend/         React app
│   └── src/
│       ├── lib/      api client, score bands
│       └── pages/    routed views
└── docs/             architecture, database, API reference
```

## Documentation

- [Architecture](docs/architecture.md)
- [Database design](docs/database.md)
- [API reference](docs/api.md)

## Future improvements

- Versioned schema migrations with Flyway, replacing Hibernate DDL generation
- Refresh tokens with rotation, and token revocation on password change
- Rate limiting on the analysis endpoint
- Background processing for analyses, with progress streamed over SSE
- Resume version comparison, so a candidate can see whether an edit actually helped
