# API reference

The API documents itself. With the backend running:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>

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
stable; `message` is display text and may change.

| Code | HTTP | Meaning |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | One or more fields failed validation |
| `BAD_REQUEST` | 400 | Request could not be interpreted |
| `UNAUTHORIZED` | 401 | Missing, expired or malformed token |
| `FORBIDDEN` | 403 | Resource belongs to another user |
| `NOT_FOUND` | 404 | No such resource for this user |
| `METHOD_NOT_ALLOWED` | 405 | Wrong HTTP verb for the path |
| `CONFLICT` | 409 | Collides with existing data, e.g. duplicate email |
| `FILE_TOO_LARGE` | 413 | Upload exceeded the configured maximum |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Content type not accepted |
| `UNSUPPORTED_FILE_TYPE` | 415 | Upload was not PDF or DOCX |
| `UNREADABLE_FILE` | 422 | No text could be extracted |
| `AI_UNAVAILABLE` | 503 | Provider unreachable or timed out |
| `AI_INVALID_RESPONSE` | 502 | Provider response failed schema validation |
| `INTERNAL_ERROR` | 500 | Unexpected fault, details in the server log only |

## Endpoints

| Method | Path | Auth | Phase |
| --- | --- | --- | --- |
| GET | `/api/health` | public | 1 |
| POST | `/api/auth/register` | public | 3 |
| POST | `/api/auth/login` | public | 3 |
| POST | `/api/resumes/upload` | bearer | 4 |
| GET | `/api/resumes` | bearer | 4 |
| GET | `/api/resumes/{id}` | bearer | 4 |
| DELETE | `/api/resumes/{id}` | bearer | 4 |
| POST | `/api/job-descriptions` | bearer | 5 |
| GET | `/api/job-descriptions` | bearer | 5 |
| POST | `/api/analyses` | bearer | 7 |
| GET | `/api/analyses` | bearer | 7 |
| GET | `/api/analyses/{id}` | bearer | 7 |
| DELETE | `/api/analyses/{id}` | bearer | 7 |
| GET | `/api/dashboard` | bearer | 7 |
| GET | `/api/recommendations` | bearer | 7 |
| GET | `/api/profile` | bearer | 7 |
| PUT | `/api/profile` | bearer | 7 |
