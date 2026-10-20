# Data model

Schema defined in `backend/src/main/resources/db/migration/V1__init.sql` and applied by Flyway.

```
users 1───* memberships *───1 organizations
  │                               │
  │ requester / assignee          │
  └──────────* tickets *──────────┘
                 │
        ┌────────┴────────┐
     comments        ticket_events
```

## Tables

### users
| Column | Type | Notes |
|---|---|---|
| id | bigserial PK | |
| email | varchar(255) | unique, stored lower-case |
| password_hash | varchar(100) | BCrypt |
| full_name | varchar(120) | |
| created_at | timestamptz | |

### organizations
| Column | Type | Notes |
|---|---|---|
| id | bigserial PK | |
| name | varchar(120) | |
| slug | varchar(60) | unique, `[a-z0-9-]` |
| created_at | timestamptz | |

### memberships — the many-to-many with an attribute
| Column | Type | Notes |
|---|---|---|
| id | bigserial PK | |
| user_id | FK users | cascade delete |
| organization_id | FK organizations | cascade delete |
| role | varchar(20) | `REQUESTER` · `TECHNICIAN` · `ADMIN` (CHECK constraint) |
| created_at | timestamptz | |

Unique `(user_id, organization_id)`: one role per person per company. The same person can be
a technician at Acme and an administrator at Globex.

### tickets
| Column | Type | Notes |
|---|---|---|
| id | bigserial PK | |
| organization_id | FK organizations | tenant key, immutable |
| title | varchar(160) | |
| description | text | |
| status | varchar(20) | `OPEN` · `IN_PROGRESS` · `RESOLVED` · `CLOSED` (CHECK) |
| priority | varchar(20) | `LOW` · `MEDIUM` · `HIGH` · `URGENT` (CHECK) |
| requester_id | FK users | immutable |
| assignee_id | FK users, nullable | must be a technician/admin of the same organization (enforced in the service) |
| version | bigint | optimistic locking, incremented on every update |
| reopen_count | int | times the ticket went `RESOLVED → OPEN` |
| created_at / updated_at | timestamptz | |
| resolved_at | timestamptz, nullable | set on resolve, cleared on reopen |
| closed_at | timestamptz, nullable | |

### comments
`id`, `ticket_id` (FK, cascade), `author_id` (FK users), `body`, `created_at`.

### ticket_events — append-only history
`id`, `ticket_id` (FK, cascade), `actor_id` (FK users), `type` (`CREATED`, `ASSIGNED`,
`UNASSIGNED`, `STATUS_CHANGED`, `PRIORITY_CHANGED`, `COMMENTED`), `old_value`, `new_value`,
`created_at`. Rows are never updated or deleted by the application.

## Indexes and why

| Index | Serves |
|---|---|
| `ix_tickets_org_status (organization_id, status)` | List filtered by status; dashboard counts by status. |
| `ix_tickets_org_assignee (organization_id, assignee_id)` | "My tickets" for a technician; workload per technician. |
| `ix_tickets_org_requester (organization_id, requester_id)` | A requester's own tickets (every list they see). |
| `ix_tickets_org_created_at (organization_id, created_at DESC)` | Default ordering, newest first, with LIMIT/OFFSET. |
| `ix_comments_ticket (ticket_id, created_at)` | Ticket detail. |
| `ix_ticket_events_ticket (ticket_id, created_at)` | Ticket history. |
| `ix_memberships_org (organization_id)` | Member list and workload join. |

Every ticket index starts with `organization_id` because every query is tenant-scoped first.
[sql/query-plans.md](sql/query-plans.md) shows `EXPLAIN ANALYZE` output with and without them.

## Normalization notes

- User identity (name, email) lives once in `users`; tickets and events reference it by id.
  Event rows store the *display value* of the change (`old_value`/`new_value`, e.g. the
  assignee's name) on purpose: history must still read correctly if a member is later renamed
  or removed.
- Role is an attribute of the membership, not of the user: 3NF, and it is the whole reason the
  join table is an entity.
- Status and priority are constrained strings rather than lookup tables; the sets are closed
  and change with code (the state machine), not with data.
