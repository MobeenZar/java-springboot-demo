# Rule-compliance task list

Fixes from the CLAUDE.md / `.claude/rules/` compliance review (2026-10-02). Each task is meant to fit in one fresh
Claude Code session.

## How to use this file (every session)

1. Start a fresh session (`/clear`) and say:
   > Read `docs/compliance-tasks.md` and do the next unchecked task. Follow its steps, then fill in its handoff note.
2. Do **only** that task. If it turns out bigger than described, stop, write what you learned in the handoff note,
   and split the rest into a new task below it.
3. Iterate with the single affected test class, then finish with `mvn verify` (must be green).
4. Tick the checkbox, fill in the **Handoff** block (what changed, decisions made, anything the next task needs),
   and make one focused commit (imperative mood) that includes this file.
5. Tasks marked **[plan first]** touch security config, the JWT filter or the login flow. CLAUDE.md requires
   planning before you edit them. Show the plan and get approval first, and prove the HTTP behaviour in
   `AuthIntegrationTest`.

Branch: `fix/rule-compliance` (see T0). One commit per task.

## Decisions to settle before the tasks that need them

| # | Question | Proposed default | Needed by |
|---|---|---|---|
| D1 | `errorCode` values for filter-chain errors | `AUTHENTICATION_REQUIRED` (no token), `INVALID_TOKEN` (bad/expired token), `ACCESS_DENIED` (403) | T6 |
| D2 | `errorCode` for 400s on login | `INVALID_REQUEST` for both validation failures and unreadable JSON | T7 |
| D3 | Response when login fails for infrastructure reasons (DB down) | 503, `errorCode: SERVICE_UNAVAILABLE`, logged at `error` with the cause | T8 |

Record the final answers here when they are made.

- **D1** (2026-10-02): proposed default accepted: `AUTHENTICATION_REQUIRED`, `INVALID_TOKEN`, `ACCESS_DENIED`.

---

## T0 — Set up the working branch
- [x] Done

**Why:** the CLAUDE.md and `.claude/rules/` commit (`dfc6c28`) is on `docs/claude-md`, not on `main`. A branch cut
from `main` would not have the rules the later tasks check against.

**Steps**
1. Merge `docs/claude-md` into `main` first (via a PR, recommended), **or** cut `fix/rule-compliance` from
   `docs/claude-md` and accept that this PR also carries the docs commit. Ask the user which one.
2. Create `fix/rule-compliance` and commit this file on it.

**Done when:** on `fix/rule-compliance`, `CLAUDE.md` and `.claude/rules/` exist, and `mvn verify` is green.

**Handoff:** (2026-10-02) The user chose to merge locally. Local `main` was fast-forwarded to `dfc6c28` (docs
commit). It is **not pushed**: `origin/main` is still at `7b50978`, so the user pushes `main` (or T10's PR includes
the docs commit). `fix/rule-compliance` was cut from `main` and has CLAUDE.md and `.claude/rules/`. `mvn verify` is
green. `docs/claude-md` still exists locally and can be deleted. Next: T1.

---

## T1 — Style fixes in production code (no behaviour change)
- [x] Done

**Files:** `config/SecurityConfig.java`, `web/AuthController.java`, `web/LoginRequest.java`, `AuthApplication.java`,
`security/ProblemDetailWriter.java`, `web/AuthExceptionHandler.java` (all under `src/main/java/com/example/auth/`)

**Steps**
1. `SecurityConfig`: reorder imports to the rule's order (static, `java.*`, `jakarta.*`, `com.*`/`io.*`, `org.*`).
   Right now `com.example.*` comes before `java.util.Map`.
2. Add Javadoc to the public types that have none: `SecurityConfig`, `AuthController`, `LoginRequest`,
   `AuthApplication`. Explain *why*/the contract (e.g. why `LoginRequest.password` is capped at 72), not what the code does.
3. `ProblemDetailWriter` Javadoc: say "RFC 9457" instead of "RFC 7807".
4. `AuthExceptionHandler`: make the two `@ExceptionHandler` methods package-private.
5. Scan the other `src/main` files for import order once more, and fix any other cases you find.

**Done when:** `mvn verify` is green, the diff has only imports, Javadoc and visibility changes.

**Handoff:** (2026-10-02) Done as described; `mvn verify` green. `SecurityConfig` imports now go `java.*` before
`com.*`. Javadoc added to `SecurityConfig`, `AuthController`, `LoginRequest` (with `@param`s; the inline 72-char
comment moved into the Javadoc) and `AuthApplication`. `ProblemDetailWriter` now says RFC 9457. Both
`AuthExceptionHandler` handlers are package-private. Step 5 scan found one more case: `JwtService` had a blank line
between `java.*` and `javax.*`, which the rule treats as one group, so they're joined now. Every other `src/main` file
was already in order, and every public type in `src/main` now has Javadoc. Next: T2.

---

## T2 — Test code style cleanups (no behaviour change)
- [x] Done

**Files:** `src/test/java/com/example/auth/AuthIntegrationTest.java`, `.../user/SecurityUserTest.java`,
`.../security/jwt/JwtServiceTest.java`

**Steps**
1. Replace the fully-qualified names written inline with imports:
   `org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder` (AuthIntegrationTest),
   `java.util.Arrays` (SecurityUserTest), `io.jsonwebtoken.security.WeakKeyException` (JwtServiceTest).
2. Check that import order follows the rule in all three files.

**Done when:** all tests are green, and no assertion changed.

**Handoff:** (2026-10-02) Done as described; `mvn verify` green (21 tests), and no assertion changed. The three inline
FQNs are now imports: `MockHttpServletRequestBuilder` (the `loginRequest` signature now fits on one line),
`java.util.Arrays`, and `WeakKeyException` (in its own `io.*` group between `java.*` and `org.*`). The import order in
all three files was already correct otherwise. A grep of `src/test/java` found no other inline FQNs. Next: T3.

---

## T3 — Add missing HTTP assertions and the expired-token HTTP test
- [x] Done

**File:** `AuthIntegrationTest.java`

These cover behaviour that already works, so the tests should pass straight away. If one fails, stop and report it.
This is a finding, not something to "fix" by loosening the test.

**Steps**
1. `invalidTokenIsRejectedEvenOnPublicEndpoint`: also assert `WWW-Authenticate` contains `invalid_token`.
2. Both 403 cases (`urlBasedRbacAllowsAdminAndForbidsUser`, `methodLevelRbacIsEnforced`): assert the
   `Content-Type` is `application/problem+json` and `$.status` is 403.
3. New test `expiredTokenIsUnauthorized`: inject `JwtProperties`, build `new JwtService(properties,
   Clock.fixed(<an hour or more ago>, UTC))` to issue a token that is already past `exp` plus the 30s skew, call `/api/me` with it,
   and expect 401 with `WWW-Authenticate` containing `invalid_token`. Don't mock `JwtService`, and don't use `Thread.sleep`.

**Done when:** `mvn test -Dtest=AuthIntegrationTest` and `mvn verify` are green.

**Handoff:** (2026-10-02) Done as described. Every new assertion passed on the first run, so there were no findings.
`mvn verify` is green (22 tests). Both 403 cases now assert `contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON)` and
`$.status == 403`, which means the method-level `@PreAuthorize` denial also goes through `RestAccessDeniedHandler`.
`expiredTokenIsUnauthorized` autowires `JwtProperties` and the context's `Clock`, and issues the token at
`clock.instant() - accessTokenTtl - clockSkew - 1h`. Because of that, it doesn't depend on `JWT_ACCESS_TOKEN_TTL`,
and it stays correct when T9 replaces the `Clock` with a fixed `@Primary` one (no `Instant.now()` added). It issues
the token for `alice` with no roles. For T6: the tests that need `$.errorCode` assertions are `missingTokenIsUnauthorized`,
`tamperedTokenIsUnauthorized`, `expiredTokenIsUnauthorized`, `invalidTokenIsRejectedEvenOnPublicEndpoint`, and both
403 tests. Next: T4 (plan first).

---

## T4 — Explicit authorization rule for `/api/me` [plan first]
- [x] Done

**File:** `config/SecurityConfig.java`

**Why:** `/api/me` is currently only protected by the `anyRequest().authenticated()` fallback. The rule says every
endpoint needs an explicit decision.

**Steps**
1. Plan: add `.requestMatchers(HttpMethod.GET, "/api/me").authenticated()` before the fallback. Keep the fallback.
2. Existing tests `missingTokenIsUnauthorized` and `loginReturnsBearerTokenUsableOnProtectedEndpoint` already prove the
   behaviour. Check that they still pass, and add nothing new unless the plan finds a gap.

**Done when:** `mvn verify` is green.

**Handoff:** (2026-10-02) Plan approved and done as described; `mvn verify` green (22 tests). `SecurityConfig` now has
`.requestMatchers(HttpMethod.GET, "/api/me").authenticated()` after the `/error` rule, and the
`anyRequest().authenticated()` fallback is kept. The planning pass checked every endpoint: `/api/admin/ping` is covered
by `/api/admin/**` → `hasRole("ADMIN")`, and `GET /api/reports` reaches the controller through the fallback but has its own
`@PreAuthorize`, which counts as its explicit decision (per the class Javadoc and the rule). So `/api/me` was the only gap,
and no test was added: `missingTokenIsUnauthorized`, `loginReturnsBearerTokenUsableOnProtectedEndpoint` and
`nullExpiryMeansPasswordNeverExpiresAndUserWithoutRolesCanLogIn` already cover 401 without a token and 200 with one, including a
user with no roles. `POST /api/me` still goes through the fallback (authenticated, then 405), as before. Next: T5.

---

## T5 — Fix `JwtService` NPE on a token without `exp`
- [x] Done

**File:** `security/jwt/JwtService.java:80`, `JwtServiceTest.java`

**Why:** `claims.getExpiration().toInstant()` throws `NullPointerException`, which becomes a 500, for a validly signed
token with no `exp`. This is a bug fix, so the test comes first.

**Steps**
1. Failing test `rejectsTokenWithoutExpiration` in `JwtServiceTest`: build a token with `Jwts.builder()` signed with the
   same test key and issuer but no `expiration(...)`. Expect `InvalidJwtException`. Run it and see it fail.
2. Fix: if `claims.getExpiration()` is null, throw `InvalidJwtException("Token has no expiration")`.
3. Check that this exception isn't caught and re-wrapped in a way that loses the message.

**Done when:** the new test passes and `mvn verify` is green.

**Handoff:** (2026-10-02) Done as described; `mvn verify` green (23 tests). `rejectsTokenWithoutExpiration` first
failed with the expected `NullPointerException`. It builds the token with `Jwts.builder()` (issuer `issuer`, subject,
`iat`, no `exp`) signed with `SECRET` via `Keys.hmacShaKeyFor`. It also asserts `hasMessage("Token has no expiration")`,
which covers step 3. `JwtService.parseAndValidate` now reads `claims.getExpiration()` into a local and throws
`InvalidJwtException("Token has no expiration")` when it is null. It isn't re-wrapped: the `catch` only takes
`JwtException | IllegalArgumentException`, and `InvalidJwtException` extends `AuthenticationException`. The filter
passes it straight to the entry point, which sends 401 `invalid_token` like any other bad token. I added no HTTP test
because the entry point branches only on the exception type, which T3 already covers. Next: T6 (plan first, needs D1).

---

## T6 — `errorCode` on filter-chain 401/403 responses [plan first]
- [x] Done

**Needs:** D1 decided.

**Files:** `security/ProblemDetailWriter.java`, `security/RestAuthenticationEntryPoint.java`,
`security/RestAccessDeniedHandler.java`, `AuthIntegrationTest.java`, `README.md` (error table)

**Why:** CLAUDE.md says every error is a `ProblemDetail` with an `errorCode`. Responses written from the filter chain
(missing token, invalid/expired token, missing role) have none.

**Steps**
1. Test first: add `jsonPath("$.errorCode")` assertions to `missingTokenIsUnauthorized`, `tamperedTokenIsUnauthorized`,
   `expiredTokenIsUnauthorized` (from T3), `invalidTokenIsRejectedEvenOnPublicEndpoint`, and both 403 cases. Run them and see them fail.
2. Add an `errorCode` parameter to `ProblemDetailWriter.write(...)` and set it as a property. Consider also setting a
   `title`, the same way `AuthExceptionHandler` does.
3. Pass the D1 codes from the entry point (two branches) and the access-denied handler.
4. Update the README error table to list the `errorCode` for each row.

**Done when:** all the new assertions pass, `mvn verify` is green, and the README matches.

**Handoff:** (2026-10-02) Used the D1 defaults; plan approved with titles included. `mvn verify` green (23 tests). All six
named tests got `$.errorCode` and `$.title` assertions and failed first with "No value at JSON path". The new signature is
`ProblemDetailWriter.write(request, response, status, errorCode, title, detail)`. It sets the title and the `errorCode`
property, using its own private `ERROR_CODE` constant rather than `web.AuthExceptionHandler`'s, so `security/` doesn't
depend on `web/`. Titles: `Authentication required`, `Invalid token`, `Access denied`. `detail` texts, statuses and
`WWW-Authenticate` headers are unchanged. The README error table now has Status / `errorCode` / Notes columns. Its
intro sentence says "Each error below" rather than "every error" because login 400s have no `errorCode` until T7. T7
should add its 400 row to this table. Next: T7 (plan first, needs D2).

---

## T7 — `errorCode` on 400 responses from login [plan first]
- [ ] Done

**Needs:** D2 decided.

**Files:** `web/AuthExceptionHandler.java`, `AuthIntegrationTest.java`, `README.md`

**Why:** `@Valid` failures (`MethodArgumentNotValidException`) and malformed JSON (`HttpMessageNotReadableException`)
currently go to Boot's default `ProblemDetailsExceptionHandler`, which has no `errorCode`.

**Gotcha:** Boot registers that handler (enabled by `spring.mvc.problemdetails.enabled: true`) as a `@ControllerAdvice`
with an `@Order`. `AuthExceptionHandler` has no order, so Boot's handler may win even after you add handlers. Check this
with the failing test. The likely fix is `@Order(Ordered.HIGHEST_PRECEDENCE)` on `AuthExceptionHandler`. It is scoped to
`AuthController`, so that is safe.

**Steps**
1. Test first: extend `blankCredentialsAreBadRequest` to assert `$.errorCode == INVALID_REQUEST`. Add
   `malformedJsonIsBadRequest` (body `{"userId":`) with the same assertions. Run them and see them fail.
2. Add handlers for both exceptions that return 400 with the D2 code. Don't echo field values, exception messages or
   parser output in `detail`. A generic message is enough (field names only, if you want them at all).
3. Add a row for 400 to the README error table.

**Done when:** the new tests pass and `mvn verify` is green.

**Handoff:**

---

## T8 — Don't report a DB outage as `INVALID_CREDENTIALS` [plan first]
- [ ] Done

**Needs:** D3 decided.

**Files:** `web/AuthExceptionHandler.java`, a test (see below), `README.md`

**Why:** `DaoAuthenticationProvider` wraps repository failures in `InternalAuthenticationServiceException`, a subtype of
`AuthenticationException`. The catch-all handler turns it into 401 `INVALID_CREDENTIALS`, logged only at `debug` with
the class name. An outage would look like users typing wrong passwords and leave no trace in the logs.

**Steps**
1. Plan the test. The testing rule says only mock what you don't own or what is slow. Simulating an outage at the
   `UserMapper` boundary (`@MockitoBean UserMapper` throwing a `DataAccessException`) is a justified exception, but put
   it in a **separate** small test class so `AuthIntegrationTest` keeps using the real database. Don't drop or rename
   tables in shared H2: other tests depend on them.
2. Test first: login returns the D3 status with `errorCode: SERVICE_UNAVAILABLE`. The response body must contain no exception
   message or SQL. Run it and see it fail.
3. Add `@ExceptionHandler(InternalAuthenticationServiceException.class)`. It is more specific than
   `AuthenticationException`, so it wins. Log at `error` with the exception, which is fine in logs but never in the response.
4. Check that unknown-user and wrong-password still return `INVALID_CREDENTIALS` (existing tests).
5. Add a README table row.

**Done when:** the new test passes and `mvn verify` is green.

**Handoff:**

---

## T9 — Fixed clock in `AuthIntegrationTest` seed data (optional)
- [ ] Done

**File:** `AuthIntegrationTest.java` (maybe a `@TestConfiguration`)

**Why:** seed data uses `LocalDateTime.now()`. The testing rule asks for a fixed `Clock` for anything time-dependent.
It's harmless today (30-day margins), so this task is low priority.

**Steps**
1. Provide a fixed `Clock` for the test context with a `@TestConfiguration` and a `@Primary` bean. Bean-definition
   overriding is off by default, so don't redefine `clock` by name.
2. Seed expiry dates relative to that clock.
3. JWT issuing and validation will both use the fixed clock, so tokens stay valid. Make sure T3's expired-token test
   still issues its token far enough in the past *relative to the fixed clock*.

**Done when:** `mvn verify` is green and there's no `LocalDateTime.now()` / `Instant.now()` left in tests.

**Handoff:**

---

## T10 — Wrap up
- [ ] Done

**Steps**
1. Re-check CLAUDE.md against the final code. In particular, the "Errors are ProblemDetail with an `errorCode`" claim
   should now hold everywhere. Update CLAUDE.md/README if anything changed meaning.
2. `mvn verify`.
3. Delete this file (or move it out of the repo), commit, push `fix/rule-compliance`, and open a PR to `main` that
   summarises T1–T9, including the decisions D1–D3.

**Handoff:**
