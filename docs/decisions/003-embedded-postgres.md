# ADR-003: Real PostgreSQL in tests and dev mode without Docker

**Status:** accepted · **Date:** 2026-10-20

## Context

The project leans on PostgreSQL-specific SQL (`FILTER`, `timestamptz`, `EXTRACT(EPOCH ...)`)
and on constraints and indexes defined in Flyway migrations. Tests against H2 would not prove
any of that. Testcontainers is the usual answer, but it needs a Docker daemon, which is not
available on every developer machine (it was not available on the machine this was built on)
and adds a service dependency to CI.

## Decision

Use `io.zonky.test:embedded-postgres`, which downloads real PostgreSQL 17 binaries from Maven
Central and starts a server process in a temp directory:

- `EmbeddedPostgresConfig` (test scope) exposes it as the application's `DataSource`, so Flyway
  migrates it exactly like production.
- Every integration test imports it through the `@IntegrationTest` meta-annotation; tables are
  truncated between tests instead of restarting the server.
- `TestServiceDeskApplication` (`mvn spring-boot:test-run`) boots the real app on it with demo
  data: a zero-setup development mode that Cypress and the demo also use.
- GitHub Actions needs no PostgreSQL service container.

## Consequences

- First run downloads ~30 MB of binaries; later runs use the Maven cache.
- The embedded server version (17.6) is pinned in `pom.xml` to match the production image
  (`postgres:17-alpine`).
- Testcontainers would be a drop-in replacement for teams with Docker everywhere; the test
  base classes are the only place that would change.
