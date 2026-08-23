# Architecture

> Filled in progressively as phases land. Phase 14 adds the full diagram set.

## Request path

```
Browser (React 18 + Vite)
   │  fetch /api/** with a Bearer token
   ▼
Spring Boot 3.2 (Tomcat)
   │  SecurityFilterChain → JwtAuthenticationFilter        [Phase 3]
   ▼
Controller (validates DTOs, no business logic)
   ▼
Service (owns the rules, enforces ownership)
   ▼
Repository (Spring Data JPA)  ──►  H2 (dev) / MySQL 8 (mysql profile)
   │
   └──► AiAnalysisClient ──► AI provider over HTTPS        [Phase 6]
```

The AI provider key lives only in the backend environment. The browser never holds it and
never calls the provider directly.

## Why feature packages

```
com.resumeiq
├── config/          cross-cutting configuration and typed properties
├── common/          error contract shared by every feature
│   ├── api/         ApiErrorResponse
│   └── exception/   ErrorCode, ApiException hierarchy, GlobalExceptionHandler
├── health/          liveness endpoint
├── user/            account entity and profile        [Phase 2]
├── auth/            registration, login, JWT          [Phase 3]
├── resume/          upload, storage, text extraction  [Phase 4]
├── jobdescription/  target roles                      [Phase 5]
├── ai/              provider clients and prompting    [Phase 6]
└── analysis/        orchestration, scores, results    [Phase 7]
```

Each package holds its own controller, service, repository and DTOs. A layer-first layout
(`controller/`, `service/`, `repository/`) scatters one capability across four packages and
hides the seams between features; grouping by feature keeps a change to resume handling
inside one directory.

## Error contract

Every failure returns the same JSON envelope with a stable `code` from the `ErrorCode`
enum. The frontend maps codes to copy, so error text can change without breaking clients,
and no client parses English. Stack traces never leave the server.

## Configuration and profiles

| Profile | Datasource | Purpose |
| --- | --- | --- |
| `dev` (default) | H2 in memory, `MODE=MySQL` | Zero-install local development |
| `mysql` | MySQL 8 from `DB_*` env vars | Production shape |

Hibernate generates the schema during development. Versioned migrations (Flyway) are a
deliberate future step, recorded in the README.
