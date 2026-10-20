# API contract

Base path `/api`. All bodies are JSON. Timestamps are ISO-8601 in UTC. The interactive
OpenAPI UI is at `/api/swagger-ui.html` when the backend runs.

## Authentication

Session cookie (`JSESSIONID`, HttpOnly, SameSite=Lax). Mutating requests must send the
`X-XSRF-TOKEN` header with the value of the readable `XSRF-TOKEN` cookie.

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/auth/register` | `{email, password (≥8), fullName}` | 201 `CurrentUser` |
| POST | `/auth/login` | `{email, password}` | 200 `CurrentUser` + session cookie; 401 `BAD_CREDENTIALS` |
| POST | `/auth/logout` | – | 204 |
| GET | `/auth/me` | – | 200 `CurrentUser`; 401 `UNAUTHENTICATED` |
| GET | `/auth/csrf` | – | 200, guarantees the `XSRF-TOKEN` cookie is set |

```json
CurrentUser {
  "id": 1, "email": "admin@acme.test", "fullName": "Alice Admin",
  "memberships": [{"organizationId": 1, "organizationName": "Acme Corp", "slug": "acme", "role": "ADMIN"}]
}
```

## Organizations and members

| Method | Path | Who | Body / Query | Response |
|---|---|---|---|---|
| POST | `/organizations` | any user | `{name, slug}` | 201 `Organization` (creator becomes ADMIN); 409 `SLUG_TAKEN` |
| GET | `/organizations` | any user | – | 200 `[Organization]` the caller belongs to |
| GET | `/organizations/{orgId}` | member | – | 200 `Organization` |
| GET | `/organizations/{orgId}/members` | member | – | 200 `[Member]` |
| POST | `/organizations/{orgId}/members` | ADMIN | `{email, role}` | 201 `Member`; 404 if no such account; 409 `ALREADY_MEMBER` |
| PATCH | `/organizations/{orgId}/members/{userId}` | ADMIN | `{role}` | 200 `Member`; 422 `LAST_ADMIN` |
| DELETE | `/organizations/{orgId}/members/{userId}` | ADMIN | – | 204; 422 `LAST_ADMIN` |

## Tickets

| Method | Path | Who | Response |
|---|---|---|---|
| POST | `/organizations/{orgId}/tickets` | member | 201 `Ticket` |
| GET | `/organizations/{orgId}/tickets` | member | 200 `Page<Ticket>` |
| GET | `/organizations/{orgId}/tickets/{id}` | member who may view | 200 `TicketDetail` |
| PATCH | `/organizations/{orgId}/tickets/{id}` | depends on change | 200 `Ticket` |
| POST | `/organizations/{orgId}/tickets/{id}/comments` | member who may view | 201 `Comment` |
| GET | `/organizations/{orgId}/tickets/{id}/events` | member who may view | 200 `[Event]` |

Requesters only see their own tickets; technicians and admins see all tickets of the organization.

### Create
```json
{"title": "Cannot sign in", "description": "Password rejected since 9am", "priority": "HIGH"}
```

### List query parameters
`status`, `priority`, `assigneeId`, `unassigned=true`, `requesterId` (ignored for requesters),
`q` (case-insensitive title search), `page` (0-based, default 0), `size` (default 20, max 100),
`sort` (`createdAt|updatedAt|priority|status|title`, `,asc|desc`; default `createdAt,desc`).

```json
Page<Ticket> {"content": [...], "page": 0, "size": 20, "totalElements": 57, "totalPages": 3}
```

### Update (PATCH)
```json
{"version": 3, "status": "IN_PROGRESS", "priority": "URGENT", "assigneeId": 2, "unassign": false}
```
`version` is required and must equal the ticket's current version. Fields are optional and
applied in the order assignment → priority → status; each produces a history event.

| Outcome | Status | `code` |
|---|---|---|
| Version is older than the current one, or a concurrent transaction won | 409 | `STALE_VERSION` |
| Transition not allowed by the state machine | 409 | `INVALID_TRANSITION` |
| Transition not allowed for the caller's role | 403 | `FORBIDDEN` |
| Moving to `IN_PROGRESS` without an assignee | 422 | `ASSIGNEE_REQUIRED` |
| Assignee is not a technician/admin | 422 | `NOT_A_TECHNICIAN` |
| Assigning a resolved/closed ticket | 422 | `TICKET_NOT_ACTIVE` |

Who may do what:

| Action | REQUESTER | TECHNICIAN | ADMIN |
|---|---|---|---|
| assign / unassign anyone | – | – | ✔ |
| claim an unassigned ticket for themselves | – | ✔ | ✔ |
| change priority | – | if assigned to them | ✔ |
| `OPEN → IN_PROGRESS`, `IN_PROGRESS → RESOLVED`, `IN_PROGRESS → OPEN` | – | if assigned to them | ✔ |
| `RESOLVED → CLOSED`, `RESOLVED → OPEN` (reopen) | own tickets | – | ✔ |

### Ticket detail
```json
{
  "ticket": {"id": 7, "title": "...", "status": "OPEN", "priority": "HIGH", "requester": {"id": 4, "fullName": "Rita"},
             "assignee": null, "version": 0, "reopenCount": 0, "createdAt": "...", "updatedAt": "..."},
  "comments": [{"id": 1, "author": {"id": 2, "fullName": "Tom"}, "body": "...", "createdAt": "..."}],
  "events": [{"id": 1, "actor": {...}, "type": "STATUS_CHANGED", "oldValue": "OPEN", "newValue": "IN_PROGRESS", "createdAt": "..."}],
  "permissions": {"allowedTransitions": ["IN_PROGRESS"], "canAssign": true, "canClaim": false, "canChangePriority": true, "canComment": true}
}
```
`permissions` tells the UI which actions are valid for *this* caller on *this* ticket right now.

## Reports

`GET /organizations/{orgId}/reports/summary?from=&to=` (ADMIN or TECHNICIAN; defaults to the
last 30 days; `[from, to)`). See [reports.md](reports.md) for the definitions.

## Errors

Every error is an RFC 9457 problem detail (`application/problem+json`) with a stable `code`:

```json
{
  "type": "https://servicedesk.dev/problems/validation-error",
  "title": "Bad Request", "status": 400, "detail": "Request validation failed",
  "code": "VALIDATION_ERROR",
  "errors": [{"field": "title", "message": "must not be blank"}]
}
```

| Status | Codes |
|---|---|
| 400 | `VALIDATION_ERROR` (with `errors[]`), `MALFORMED_REQUEST` |
| 401 | `UNAUTHENTICATED`, `BAD_CREDENTIALS` |
| 403 | `FORBIDDEN` (also returned for a missing CSRF token) |
| 404 | `NOT_FOUND` (includes "not a member of this organization") |
| 409 | `STALE_VERSION`, `INVALID_TRANSITION`, `EMAIL_TAKEN`, `SLUG_TAKEN`, `ALREADY_MEMBER`, `DATA_INTEGRITY` |
| 422 | `ASSIGNEE_REQUIRED`, `NOT_A_TECHNICIAN`, `TICKET_NOT_ACTIVE`, `IN_PROGRESS_NEEDS_ASSIGNEE`, `LAST_ADMIN` |
