# Database design

> Entities, relationships and the ER diagram land in Phase 2.

Planned tables: `users`, `resumes`, `job_descriptions`, `analyses`, `skills`,
`analysis_skills`, `recommendations`.

Two constraints shape the design from the start:

- Every row that belongs to a person is reachable from a `user_id`, so ownership can be
  enforced in a single query rather than by walking relationships in Java.
- Extracted resume text is stored as `LONGTEXT`/`CLOB` and is never returned by list
  endpoints, only by the analysis pipeline that needs it.
