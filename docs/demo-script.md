# Demo script (8–10 minutes, in English)

Prepare: backend running (`./mvnw spring-boot:test-run`), frontend running (`npm run dev`),
two browser windows (admin and technician), terminal with the test suite ready.

## 1. The business problem and the users (1 min)

"Small companies handle IT requests over chat and email: nothing is tracked, nobody knows
who is working on what, and the manager cannot see response times. ServiceDesk gives each
company a workspace with three roles: requesters open tickets, technicians work them,
administrators assign work and read indicators."

## 2. Functional walkthrough (3 min)

1. Sign in as `user@acme.test` (requester). Create a ticket: "Cannot access my account".
   Point out validation (submit empty) and that the requester only sees their own tickets.
2. Sign in as `admin@acme.test` in the second window. Open the ticket, assign Tom. Show the
   history entry appearing with the actor.
3. Sign in as `tech@acme.test`. "Start working", post a comment asking for details, "Mark as
   resolved". Show the status badge and `resolved_at`.
4. Back as the requester: "Close". Show the full history: created → assigned → in progress →
   comment → resolved → closed, each with who and when.
5. Dashboard as admin: open tickets, distribution by status, workload per technician,
   average resolution time; change the period.
6. Try `admin@globex.test` and navigate to Acme's URL: redirected, and the API answers 404.

## 3. Architecture and data model (2 min)

- Modular monolith: identity, organizations, tickets, reports. Controller validates, service
  authorizes per organization, domain enforces the state machine.
- Data model on the whiteboard: `memberships` is the many-to-many with a role; tickets carry
  `organization_id`, `version`, timestamps; events are append-only.
- Session cookie + CSRF double-submit; why not JWT for a same-origin SPA (ADR-001).

## 4. A bug and how it was fixed (1.5 min)

The CSRF cookie that vanished in tests (`docs/debugging-case.md`): symptom, isolation vs
suite, root cause in the test helper mutating the filter chain, the fix (test the browser flow
over a real port), and the regression test.

## 5. One technical decision and its alternatives (1.5 min)

Optimistic locking (ADR-002). Show `ConcurrencyIT`: two clients with version 0, the second
gets `409 STALE_VERSION`; and the two-thread race test. Show the UI message with "Reload
ticket". Alternatives: last-write-wins, pessimistic locks, and why they do not fit HTTP.

## 6. Close (30 s)

Run `./mvnw test` and `npm test` on screen. Mention CI, Docker Compose, the `EXPLAIN ANALYZE`
evidence for the indexes, and what is intentionally out of scope (attachments, real-time,
mobile, payments).

## Likely questions

- *Why 404 and not 403 for another company's ticket?* Not revealing existence of resources
  outside the tenant.
- *What happens to the history if a user is deleted?* Events store display values and the
  actor FK is not cascaded; members are removed from the organization, accounts are kept.
- *How would you scale it?* Spring Session for shared sessions, read replicas for reports,
  then extract `reports` as the first service if load justifies it.
- *How are reopened tickets counted?* `docs/reports.md`: once, at the latest resolution,
  with the full elapsed time; `reopen_count` reported separately.
