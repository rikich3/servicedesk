# ADR-002: Optimistic locking with an explicit client version

**Status:** accepted · **Date:** 2026-10-20

## Context

Two technicians (or a technician and an administrator) can open the same ticket and submit
changes seconds apart. Without protection, the second write silently overwrites the first:
an assignment is lost, or a ticket is resolved on top of a priority change nobody saw.

## Options

1. **Last write wins.** Simplest; loses data silently.
2. **Pessimistic locks (`SELECT ... FOR UPDATE`).** Correct, but locks would have to span the
   user's think time, which is impossible over HTTP; locks per request only serialize the
   database write, they do not detect a stale screen.
3. **Optimistic locking (chosen).** A `version` column; the client echoes the version it
   rendered; the server rejects updates based on an older version.

## Decision

`Ticket.version` is a JPA `@Version` column. `PATCH /tickets/{id}` requires `version`:

- The service compares it with the loaded entity first and fails fast with
  `409 STALE_VERSION` when the client is behind. This catches the common case (stale screen)
  with a clear message and no partial work.
- Hibernate's own version check protects against two transactions that both loaded the same
  version and race to commit: the loser gets `ObjectOptimisticLockingFailureException`, mapped
  to the same `409 STALE_VERSION`.

The UI shows "Someone else changed this ticket while you were editing" with a reload action.

## Evidence

`ConcurrencyIT` covers both layers: the API-level stale version (second PATCH with version 0
after a successful update) and a true race with two threads and a latch (exactly one `ok`, one
`conflict`, final version 1).
