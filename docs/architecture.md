# Architecture

## Shape: a modular monolith

One Spring Boot application, one PostgreSQL database, one React SPA. The backend is organized
by feature rather than by technical layer, so each package owns its entities, repositories,
services, DTOs and controller:

```
com.servicedesk
├── common         Problem-detail error handling, shared exceptions, PageResponse
├── identity       User, session login/logout, Spring Security configuration, CSRF
├── organizations  Organization, Membership (user ↔ org with a role), AccessGuard
├── tickets        Ticket aggregate, state machine, TicketPolicy, comments, history
├── reports        Hand-written SQL indicators (JdbcClient)
└── config         Clock, OpenAPI, demo-data seeder
```

Dependencies point inwards: `tickets` and `reports` depend on `organizations` (for the
membership guard) and `identity` (for `User`), never the other way round. Splitting any module
into its own service later would mostly be a matter of replacing the in-process calls with HTTP.

## Request flow

```
Browser ──(session cookie + X-XSRF-TOKEN)──► Spring Security filter chain
                                                  │ authenticates the session, validates CSRF
                                                  ▼
                                             @RestController      validates the request body (Bean Validation),
                                                  │               extracts the principal, delegates
                                                  ▼
                                             @Service             @Transactional use case:
                                                  │               AccessGuard.requireMember/requireRole(orgId, userId)
                                                  │               TicketPolicy decides what this role may do
                                                  ▼
                                             Domain (Ticket)      enforces the state machine, timestamps, assignment rules
                                                  ▼
                                             JPA repositories / JdbcClient ──► PostgreSQL
```

The three layers answer different questions: the controller asks "is this a well-formed
request?", the service asks "is this caller allowed to do this to this organization's data?",
and the domain asks "does this change make sense for a ticket in this state?". Errors from each
layer map to distinct HTTP responses (400, 403/404, 409/422) through one `@RestControllerAdvice`.

## Multi-tenancy

Every tenant-scoped URL carries the organization id (`/api/organizations/{orgId}/...`). The
service layer always starts by resolving the caller's `Membership` in that organization:

- Not a member → `404 Not Found`. We deliberately do not reveal that the organization exists.
- Member without the required role → `403 Forbidden`.
- Ticket lookups are organization-scoped SQL (`where id = ? and organization_id = ?`), so a
  valid ticket id from another tenant is also a 404.

Organization roles are **not** Spring Security authorities: a user can be an admin in one
company and a requester in another, and the role only makes sense relative to the organization
in the URL. `TenantIsolationIT` exercises every endpoint from the perspective of an outsider.

## Security model

- Authentication: username/password against BCrypt hashes, kept in an HTTP session.
  The session id travels in an `HttpOnly; SameSite=Lax` cookie, so scripts never see it.
- CSRF: Spring Security's double-submit cookie. The server issues a readable `XSRF-TOKEN`
  cookie; the SPA mirrors it into the `X-XSRF-TOKEN` header on every mutation. Login itself is
  protected too (prevents login CSRF). `CsrfCookieFlowIT` tests this over a real HTTP port.
- Authorization: membership and role checks in the service layer, per request.
- Unauthenticated and forbidden responses are JSON problem details, never redirects.

See [ADR-001](decisions/001-session-cookie-auth.md) for why sessions rather than JWT.

## Concurrency and consistency

- `Ticket.version` is a JPA `@Version` column. Clients send the version they saw in every
  `PATCH`; the service refuses a stale version with `409 STALE_VERSION`, and Hibernate's
  optimistic lock catches two transactions racing on the same row ([ADR-002](decisions/002-optimistic-locking.md)).
- A ticket update and its `ticket_events` rows are written in one `@Transactional` method.
  `TransactionAtomicityIT` makes the event write fail and asserts that the ticket is unchanged.

## Frontend

Vite + React + TypeScript + Tailwind. The SPA is served from the same origin as the API
(Vite proxy in development, nginx in Docker), which is what makes the cookie-based model work
without CORS. A small fetch wrapper (`src/api/client.ts`) centralizes credentials, the CSRF
header, problem-detail parsing (`ApiError` with a stable `code`) and network failures
(`NetworkError`). Pages render explicit loading, empty, error and conflict states; the ticket
detail renders only the actions the backend says the caller may take (`permissions`).

## Local development and CI without Docker

Integration tests and the development entry point start a real PostgreSQL through
`zonky embedded-postgres`, so the suite and the demo run on any machine (and on GitHub Actions)
without a database service or a Docker daemon ([ADR-003](decisions/003-embedded-postgres.md)).
`docker-compose.yml` remains the reference deployment shape.
