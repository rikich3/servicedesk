# ADR-001: Session cookie authentication instead of JWT

**Status:** accepted · **Date:** 2026-10-20

## Context

The React SPA and the API are served from the same origin (Vite proxy in development, nginx
in Docker). We need authentication that is safe by default for a browser client, supports
logout, and is simple to explain and test.

## Options

1. **HTTP session + HttpOnly cookie (chosen).** Spring Security's default model. Credential
   never visible to JavaScript; server-side invalidation on logout; needs CSRF protection.
2. **JWT in `Authorization: Bearer`.** Stateless; convenient for mobile or third-party clients;
   the token must be stored somewhere readable by JS (XSS exposure) or in a cookie anyway;
   logout and revocation need extra infrastructure (denylist, short expiry + refresh tokens).
3. **JWT in an HttpOnly cookie.** Same CSRF needs as sessions, plus the revocation problem.

## Decision

Use the servlet session with an `HttpOnly; SameSite=Lax` cookie, and Spring Security's
double-submit CSRF cookie (`XSRF-TOKEN` → `X-XSRF-TOKEN`) with the SPA request handler
recommended in the reference documentation. Login and logout are JSON endpoints
(`/api/auth/login`, `/api/auth/logout`) rather than form login, and failures return problem
details instead of redirects.

## Consequences

- Sessions live in memory in the single backend instance. Horizontal scaling would require
  Spring Session (e.g. backed by PostgreSQL or Redis). Acceptable for the MVP.
- A future mobile client would get its own token-based scheme; the service layer does not
  depend on how the principal was authenticated.
- CSRF is tested end to end over a real port (`CsrfCookieFlowIT`), which also documents the
  exact browser flow for anyone writing another client.
