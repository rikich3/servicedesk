# Reports and indicators

`GET /api/organizations/{orgId}/reports/summary?from=&to=` returns the dashboard numbers.
All queries are hand-written SQL in `ReportService` and run inside PostgreSQL; nothing is
aggregated in Java. The period is half-open: `from <= t < to`.

| Field | Definition | SQL idea |
|---|---|---|
| `openTickets` | Tickets currently `OPEN` or `IN_PROGRESS` | `COUNT(*) ... GROUP BY status` |
| `unassignedTickets` | Open tickets with no assignee | `WHERE status IN (...) AND assignee_id IS NULL` |
| `byStatus` | Current count per status (all four keys always present) | `GROUP BY status` |
| `byPriority` | Current count of *open* tickets per priority | `GROUP BY priority` |
| `createdInPeriod` | Tickets created in the period | `COUNT(*) FILTER (WHERE created_at >= :from AND created_at < :to)` |
| `resolvedInPeriod` | Tickets whose current `resolved_at` falls in the period | `COUNT(*) FILTER (WHERE resolved_at >= :from AND resolved_at < :to)` |
| `avgResolutionHours` | Mean of `resolved_at - created_at` over `resolvedInPeriod`; `null` when none | `AVG(EXTRACT(EPOCH FROM (resolved_at - created_at)) / 3600.0) FILTER (...)` |
| `reopenedTickets` | Tickets reopened at least once (ever) | `COUNT(*) FILTER (WHERE reopen_count > 0)` |
| `workload[]` | One row per technician/admin (see below) | `memberships JOIN users LEFT JOIN tickets ... GROUP BY u.id` |

Per technician: `activeAssigned` (open or in progress and assigned to them), `inProgress`,
`resolvedInPeriod`, `avgResolutionHours` (same definitions, restricted to their tickets).
Members with no tickets still appear with zeros thanks to the `LEFT JOIN`.

## How reopened tickets are treated

When a ticket goes `RESOLVED → OPEN`, `resolved_at` is cleared and `reopen_count` incremented.
When it is resolved again, `resolved_at` is set to the new time. Consequences:

- A ticket is counted at most once in `resolvedInPeriod`, in the period of its *latest*
  resolution. A ticket that was resolved last month and reopened this month disappears from
  last month's count: it was not actually resolved.
- Resolution time is measured from creation to the latest resolution, so the rework caused by
  a premature "resolved" is included in the average. This is deliberate: the indicator should
  not reward closing tickets early.
- `reopenedTickets` is reported alongside so the reader can see how much rework there is.

The FILTER clauses let a single pass over the organization's tickets compute all period
columns; the workload query is the one JOIN + aggregation that benefits most from
`ix_tickets_org_assignee` (see [sql/query-plans.md](sql/query-plans.md)).
