# Debugging case: the CSRF cookie that disappeared in tests

A real bug hit while building this project, written up the way an issue would be.

## Symptom

`AuthFlowIT.unauthenticatedRequestsGet401ProblemDetail` asserted that the first response from
the API sets the `XSRF-TOKEN` cookie (the SPA needs it before its first `POST`). The assertion
failed: no `Set-Cookie` header. Yet a one-off test doing exactly the same request, run alone,
saw the cookie. Running the failing test class alone still failed.

## Reproduction

1. In a MockMvc test class, perform any request with `.with(csrf())` (Spring Security's test
   post-processor), e.g. a login.
2. In another test of the same class (or any class sharing the cached context), perform a plain
   `GET /api/auth/me` and inspect the response cookies.

Order matters: the plain request only lacks the cookie after a `csrf()` request has run.

## Root cause

`SecurityMockMvcRequestPostProcessors.csrf()` does not just add a token to the request. It
wraps the `CsrfTokenRepository` inside the application's `CsrfFilter` with a
`TestCsrfTokenRepository` (via reflection) and leaves it there. From then on, requests that are
not marked by `csrf()` get a test token that is never saved as a cookie. The production code
was correct; the test harness had mutated the shared security filter chain.

## Fix

- Keep MockMvc tests for authorization and business rules (they are fast and precise).
- Test the real browser flow where it belongs: `CsrfCookieFlowIT` boots the app on a random
  port and uses `RestClient` to check that (1) the first response sets a readable
  `XSRF-TOKEN` cookie, (2) a login without the mirrored header is rejected with 403, (3) a login
  with the header succeeds and sets an `HttpOnly; SameSite=Lax` session cookie.

## Regression test

`CsrfCookieFlowIT.doubleSubmitCookieProtectsLogin`. It runs in its own Spring context
(`RANDOM_PORT`), so no MockMvc post-processor can interfere with it.

## Lessons

- A test passing in isolation and failing in a suite points at shared mutable state. Here the
  state was inside a Spring bean, not in the database.
- When the thing under test is "what the browser receives", test it over HTTP. MockMvc skips
  parts of the servlet container and, as seen here, test helpers may alter the chain.
