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
