# ServiceDesk

A multi-tenant support ticketing platform for small companies. Each company gets its own
workspace where **requesters** open tickets, **technicians** work them, and **administrators**
manage members, assignments and indicators.

Built with **Java 21 · Spring Boot 4 · PostgreSQL · React 19 · TypeScript · Tailwind CSS**.

[![CI](https://github.com/rikich3/servicedesk/actions/workflows/ci.yml/badge.svg)](https://github.com/rikich3/servicedesk/actions/workflows/ci.yml)

## What it does

| Role | Can |
|---|---|
| Requester | Create tickets, follow their own tickets, comment, close or reopen a resolved ticket. |
| Technician | See every ticket in the workspace, claim unassigned ones, drive the tickets assigned to them. |
| Administrator | Everything above, plus assign/unassign anyone, manage members and roles, read the dashboard. |

Ticket lifecycle: `OPEN → IN_PROGRESS → RESOLVED → CLOSED`, with `IN_PROGRESS → OPEN` (hand back
to the queue) and `RESOLVED → OPEN` (reopen). `CLOSED` is terminal. Every change is written to an
append-only history in the same transaction as the change itself.

## Quick start

### Option A: no Docker, nothing to install besides Java 21 and Node 22

```bash
# Terminal 1: API on :8080 with an embedded PostgreSQL and demo data
cd backend && ./mvnw spring-boot:test-run

# Terminal 2: SPA on :5173 (proxies /api to :8080)
cd frontend && npm install && npm run dev
```

Open <http://localhost:5173> and sign in with one of the demo accounts (password `password`):

| Account | Role in Acme Corp |
|---|---|
| `admin@acme.test` | Administrator |
| `tech@acme.test`, `tech2@acme.test` | Technician |
| `user@acme.test`, `user2@acme.test` | Requester |
| `admin@globex.test` | Administrator of a *different* company (Globex) |

The embedded database is thrown away when the process stops.

### Option B: Docker Compose (PostgreSQL + API + SPA behind nginx)

```bash
docker compose up --build
# SPA on http://localhost:3000, API on http://localhost:8080, PostgreSQL on :5432
```

### Option C: your own PostgreSQL

```bash
createdb servicedesk
cd backend && DB_URL=jdbc:postgresql://localhost:5432/servicedesk DB_USER=... DB_PASSWORD=... \
  SEED_DEMO_DATA=true ./mvnw spring-boot:run
```

Flyway creates the schema on first start.

## Running the tests

| Layer | Command | What it covers |
|---|---|---|
| Backend unit + integration | `cd backend && ./mvnw test` | State machine, permissions, tenant isolation, optimistic locking, transactional history, SQL reports, CSRF/session flow. Integration tests run on a real embedded PostgreSQL, no Docker daemon required. |
| Frontend unit | `cd frontend && npm test` | Fetch client (CSRF header, problem-detail parsing, network errors), login/validation flows, ticket form, ticket detail incl. 409 conflict handling. |
| End to end | start Option A, then `cd frontend && npm run cy:run` | Login → create → assign → resolve → close → history, across three roles; wrong credentials; cross-tenant navigation. |
| Query plans | `cd backend && ./mvnw test -Dtest=QueryPlanIT -Dservicedesk.plans=true` | Regenerates [docs/sql/query-plans.md](docs/sql/query-plans.md) with `EXPLAIN ANALYZE` over 60k tickets. |

API documentation (OpenAPI) is served at <http://localhost:8080/api/swagger-ui.html>.

## Repository layout

```
backend/   Spring Boot modular monolith (identity, organizations, tickets, reports)
frontend/  Vite + React + TypeScript SPA, Vitest and Cypress
docs/      Architecture, data model, API contract, decisions, demo script, query plans
.github/   CI: backend tests, frontend tests/build, Cypress e2e
```

## Documentation

- [Architecture](docs/architecture.md) – modules, request flow, security model
- [Data model](docs/data-model.md) – entities, constraints, indexes
- [API contract](docs/api.md) – endpoints, error format, pagination
- [Reports and indicators](docs/reports.md) – what each number means and the SQL behind it
- [Architecture decisions](docs/decisions/) – session cookies vs JWT, optimistic locking, embedded PostgreSQL
- [Debugging case](docs/debugging-case.md) – a real bug found while building this, with root cause and regression test
- [Query plans](docs/sql/query-plans.md) – `EXPLAIN ANALYZE` evidence for the chosen indexes
- [Demo script](docs/demo-script.md) – 8–10 minute walkthrough
- [Project plan](docs/project-plan.md) – milestones, issue breakdown, out of scope

## Traceability

Requirements are tracked as [issues](https://github.com/rikich3/servicedesk/issues?q=is%3Aissue) grouped in six
[milestones](https://github.com/rikich3/servicedesk/milestones?state=all); changes land through
[pull requests](https://github.com/rikich3/servicedesk/pulls?q=is%3Apr) that link their issue and the tests that prove them.
The [CI workflow](https://github.com/rikich3/servicedesk/actions) runs backend, frontend and Cypress jobs on every pull request.

## Status

MVP complete: authentication and per-organization authorization, ticket management with
filters and pagination, comments and history, dashboard, member management. Attachments,
payments, real-time chat and a mobile client are intentionally out of scope for this stage.
