# Project plan

Six milestones, each with a verifiable result. Suggested GitHub issues per milestone so the
work is traceable from requirement → issue → pull request → test.

## Milestone 1 – Foundations
- [x] Requirements and acceptance criteria (README, this plan)
- [x] Data model and Flyway migration `V1__init.sql`
- [x] Local environment: embedded PostgreSQL dev mode, Docker Compose

## Milestone 2 – Identity and organizations
- [x] Register / login / logout / me with session cookie and CSRF
- [x] Organizations, memberships with roles, last-admin rule
- [x] `AccessGuard` and tenant-isolation tests

## Milestone 3 – Tickets
- [x] Create, list with filters/pagination/sorting, detail
- [x] State machine with explicit transitions and reopen rule
- [x] Assignment and claim rules per role (`TicketPolicy`)
- [x] Optimistic locking with `409 STALE_VERSION`
- [x] Comments and append-only history in one transaction

## Milestone 4 – React UI
- [x] Login/register, workspace picker
- [x] Ticket list (filters, empty/loading/error states), new ticket form
- [x] Ticket detail with role-aware actions, conflict handling, comments, history
- [x] Members page, dashboard

## Milestone 5 – Reports and test depth
- [x] SQL indicators with documented definitions
- [x] Integration tests on real PostgreSQL (56 backend tests)
- [x] Vitest component tests (18), Cypress journeys (2)
- [x] `EXPLAIN ANALYZE` evidence for indexes

## Milestone 6 – Delivery
- [x] GitHub Actions: backend, frontend, e2e
- [x] Dockerfiles and Compose stack
- [x] Documentation and demo script
- [ ] Deploy the Compose stack to a small VM (AWS Lightsail / EC2) – next step
- [ ] Peer review: ask a colleague to review one PR and answer their comments

## Working agreements
- One branch per issue (`feature/<issue>-short-name`), PR with context, screenshots and the
  tests that prove the change; squash-merge to `main`.
- Weekly note in the project board: done / next / blocked.
- UI, code, commits, issues and PRs in English.

## Out of scope for this stage
Attachments, payments/billing, real-time chat, mobile app, email notifications, SSO.
Each has a natural extension point (object storage for attachments, Spring events or a queue
for notifications, a token-based auth scheme for mobile).
