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
| D4 | Framework MVC errors (404/405/406/415, unhandled 500) have no `errorCode` (found in T10). Fix in code, or narrow the CLAUDE.md claim? | Fix in code (T11): a lowest-precedence global advice that adds an `errorCode` and a generic `detail` | T11, T10 |

Record the final answers here when they are made.

- **D1** (2026-10-02): proposed default accepted: `AUTHENTICATION_REQUIRED`, `INVALID_TOKEN`, `ACCESS_DENIED`.
- **D2** (2026-10-02): proposed default accepted: `INVALID_REQUEST` for both validation failures and unreadable JSON.
- **D3** (2026-10-02): proposed default accepted: 503, `errorCode: SERVICE_UNAVAILABLE`, logged at `error` with the cause.
- **D4** (2026-10-02): proposed default accepted: fix in code (T11).

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
- [x] Done

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

**Handoff:** (2026-10-02) Used the D2 default; plan approved, with an extra 73-char password test added on request.
`mvn verify` green (25 tests). The three 400 tests (`blankCredentialsAreBadRequest`, new `malformedJsonIsBadRequest`,
new `passwordLongerThanBcryptLimitIsBadRequest`) share an `invalidRequest()` `ResultMatcher`. It checks 400,
`problem+json`, `errorCode: INVALID_REQUEST`, `title: Invalid request` and the exact fixed `detail` "The request
body is missing or invalid", which proves nothing is echoed. All three failed first. **The gotcha was real:** with the
handler added but no `@Order`, Boot's `ProblemDetailsExceptionHandler` still answered (same failures). Adding
`@Order(Ordered.HIGHEST_PRECEDENCE)` fixed it, and the class Javadoc explains why. One handler covers both
`MethodArgumentNotValidException` and `HttpMessageNotReadableException`, logged at `debug` with the class name only. The
ordering doesn't change the existing 401 handlers, because Boot's advice doesn't handle `AuthenticationException`.
README has a 400 row. For T8: `AuthExceptionHandler` now runs before Boot's advice. The new
`InternalAuthenticationServiceException` handler goes in the same class, and the more specific type wins within
one advice. Next: T8 (plan first, needs D3).

---

## T8 — Don't report a DB outage as `INVALID_CREDENTIALS` [plan first]
- [x] Done

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

**Handoff:** (2026-10-02) Used the D3 default; plan approved. `mvn verify` green (27 tests). New
`LoginOutageTest` (`@SpringBootTest` + `@MockitoBean UserMapper`) throws `DataAccessResourceFailureException` with a
fake JDBC URL and `APP_USERS` in the message. `databaseOutageDuringLoginIsServiceUnavailable` checks 503,
`problem+json`, `SERVICE_UNAVAILABLE`, title, the fixed detail, and that neither host nor table name is in the body.
`databaseOutageIsLoggedAtErrorWithCause` uses `OutputCaptureExtension` to check for an `ERROR` line from
`AuthExceptionHandler` and the cause message. Both failed first (401, no error log). `AuthExceptionHandler` now
has an `InternalAuthenticationServiceException` handler that logs `log.error(..., ex)`. Its Javadoc notes that this
also turns other unexpected user-loading failures into 503 instead of a misleading 401.
**Found on the way:** the second context failed to start. `jdbc:h2:mem:authdb;DB_CLOSE_DELAY=-1` keeps the database
alive across contexts, so `test-schema.sql`'s `CREATE TABLE` ran twice. I fixed it in the new class only, with
`@SpringBootTest(properties = "spring.sql.init.mode=never")`, which is safe because the mapper is mocked. I didn't
change `test-schema.sql` and didn't drop anything. For T9: if T9 adds a `@TestConfiguration` with `@Primary Clock`
to `AuthIntegrationTest` only, that also gives it a separate context. That's fine (its schema init runs first or is
skipped here), but don't add schema init to any other new context. Next: T9 (optional).

---

## T9 — Fixed clock in `AuthIntegrationTest` seed data (optional)
- [x] Done

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

**Handoff:** (2026-10-02) Done as described; `mvn verify` green (27 tests). `AuthIntegrationTest` has a nested
`@TestConfiguration(proxyBeanMethods = false) FixedClockConfig` with a `@Primary Clock fixedClock()` bean fixed at
`2026-01-01T10:00:00Z` (UTC, the same instant `SecurityUserTest` uses). The method is named `fixedClock` so it
doesn't collide with `ClockConfig.clock`. The autowired `clock` field and `JwtService`/`DatabaseUserDetailsService`
all get the fixed one through `@Primary`. Seed data now uses `LocalDateTime.now(clock)`. `expiredTokenIsUnauthorized`
needed no change, because it already computes its issue time from `clock.instant()`. A grep of `src/test` finds no
argument-less `.now()` left. As T8 predicted, `AuthIntegrationTest` now has its own context. It is the only context
that runs schema init (`LoginOutageTest` skips it), so there's no double `CREATE TABLE`. Next: T10.

---

## T10 — Wrap up
- [x] Done

**Steps**
1. Re-check CLAUDE.md against the final code. In particular, the "Errors are ProblemDetail with an `errorCode`" claim
   should now hold everywhere. Update CLAUDE.md/README if anything changed meaning.
2. `mvn verify`.
3. Delete this file (or move it out of the repo), commit, push `fix/rule-compliance`, and open a PR to `main` that
   summarises T1–T9, including the decisions D1–D3.

**Handoff:** (2026-10-02) **Stopped at step 1, which turned out bigger than described.** Re-checking CLAUDE.md found
that "Errors are `ProblemDetail` with an `errorCode`" holds for everything in the README table (login, filter chain),
but **not** for errors Spring MVC raises itself. A throwaway probe test (since deleted) on a context with a valid token showed:

| Request | Status | Body |
|---|---|---|
| `GET /api/nope` | 404 | `problem+json`, no `errorCode`, detail `No static resource api/nope.` |
| `POST /api/me` | 405 | `problem+json`, no `errorCode`, detail `Method 'POST' is not supported.` |
| `POST /api/auth/login` as `text/plain` | 415 | `problem+json`, no `errorCode`, detail echoes the request `Content-Type` |
| `GET /api/me` with `Accept: application/xml` | 406 | `problem+json`, no `errorCode` |

All four come from Boot's `ProblemDetailsExceptionHandler`. The 415 one gets past `AuthExceptionHandler` even though
that advice is scoped to `AuthController`, because it has no handler for that exception. No stack traces or SQL leak, but
the `detail` texts echo request data. Not probed, because no endpoint throws one: an unhandled `RuntimeException` would
fall through to Boot's `/error` (`BasicErrorController`), which returns plain JSON with no `errorCode`
(`include-message: never` keeps the message out). Fixing this in code or narrowing the CLAUDE.md claim is a "key decision"
change, so it is D4, and the code work is split into T11. Nothing else in CLAUDE.md/README drifted from the code. Steps 2–3
are not done: nothing pushed, no PR. Note from T0: `origin/main` is still at `7b50978`, so the PR will also carry the
CLAUDE.md docs commit `dfc6c28` unless `main` is pushed first. Next: settle D4, then T11, then finish T10.

**Handoff (finish):** (2026-10-02, after T11) Step 1 re-done against the final code. Every other CLAUDE.md claim
holds: `JwtAuthenticationFilter` is built with `new` in `SecurityConfig` and isn't a bean, there is no argument-less
`.now()` in `src/main`, roles are normalised in `SecurityUser`, the `{bcrypt}` fallback is in `passwordEncoder()`,
`LoginRequest.password` has `@Size(max = 72)`, the one `#{userId}` has a `jdbcType`, and `test-schema.sql` matches the
mapper's columns. The `errorCode` claim was still too broad, as T11 noted, so I narrowed it in CLAUDE.md to "errors from
controllers, Spring MVC and the security filter chain". The new wording names Boot's `/error` (plain JSON, no `errorCode`)
as the remaining gap for errors raised outside MVC, such as a servlet filter. The README has a matching "Not covered"
note under the table. `.claude/rules/java-spring.md` now names `GlobalExceptionHandler` as the one deliberate exception
to the "scoped advice" rule. `mvn verify` green (32 tests). A stale `ZzProbeTest` report left in `target/` by T10's
probe has no source or class. Then this file was deleted in a separate commit, so this handoff stays in history,
the branch was pushed and the PR was opened.
**Branch state:** local `main` is at `495cc2d`, 12 ahead of `origin/main` (`7b50978`), and was never pushed. The PR goes to
`origin/main`, so it carries everything from `dfc6c28` (CLAUDE.md docs) onwards.

---

## T11 — `errorCode` on framework MVC errors [plan first]
- [x] Done

**Needs:** D4 decided (if D4 says "narrow the claim", skip this task and reword CLAUDE.md line 27 in T10 instead).

**Files:** a new global `@RestControllerAdvice` (e.g. `web/GlobalExceptionHandler.java`) or a customised
`ResponseEntityExceptionHandler` subclass, `AuthIntegrationTest.java`, `README.md`, maybe `application.yml`

**Steps**
1. Test first, in `AuthIntegrationTest`: authenticated `GET` on an unknown path → 404, `POST /api/me` → 405, login as
   `text/plain` → 415. Assert `problem+json`, an `errorCode` (proposal: `NOT_FOUND`, `METHOD_NOT_ALLOWED`,
   `UNSUPPORTED_MEDIA_TYPE`), and that `detail` doesn't echo the path, method or content type. Run them and see them fail.
2. Option to plan: subclass `ResponseEntityExceptionHandler` once (lowest precedence, so `AuthExceptionHandler` still
   wins), override `handleExceptionInternal`/`createProblemDetail` to add `errorCode` from the status and replace
   `detail` with a generic text, and set `spring.mvc.problemdetails.enabled: false` so Boot's copy doesn't answer
   first. Add a catch-all `Exception` handler → 500 `INTERNAL_ERROR`, logged at `error`.
3. Make sure `AuthExceptionHandler`'s 400/401/503 responses are unchanged (existing tests).
4. README: add a short "other errors" row or note.

**Done when:** the new tests pass, `mvn verify` is green, and the claim in CLAUDE.md holds. Then go back to T10.

**Handoff:** (2026-10-02) Used the D4 default; plan approved. `mvn verify` green (32 tests). New tests in
`AuthIntegrationTest`: `unknownPathIsNotFound`, `unsupportedMethodIsMethodNotAllowed` (also asserts `Allow: GET`),
`unsupportedContentTypeOnLoginIsRejected`, `unacceptableMediaTypeIsRejected` (406, added beyond step 1) and
`unexpectedExceptionIsInternalErrorWithoutDetails`. They share a `frameworkProblem(...)` matcher that checks status,
`problem+json`, `errorCode`, title (= reason phrase) and an exact fixed `detail`. All five failed first. The 500 test
uses a nested `FailingController` brought in by `@Import` on `FixedClockConfig`. Boot's test filter keeps nested test
classes out of component scanning, so `LoginOutageTest` doesn't get it. The test also checks the `ERROR` log line with
`OutputCaptureExtension`.
New `web/GlobalExceptionHandler` extends `ResponseEntityExceptionHandler`, with `@Order(LOWEST_PRECEDENCE)`, and is
**deliberately unscoped**: 404/405 are raised before a controller is chosen, so a scoped advice never applies. This
deviates from the "scoped advice" rule, and the Javadoc explains why. It overrides `createResponseEntity` to set the title,
a fixed `detail` per status, and the `errorCode` (400 → `INVALID_REQUEST`, 500 → `INTERNAL_ERROR`, otherwise the
`HttpStatus` name). Spring's headers are kept. A catch-all `Exception` handler returns 500 and logs at `error`.
`AccessDeniedException`/`AuthenticationException` are rethrown untouched, so `@PreAuthorize` denials still reach
`RestAccessDeniedHandler` (`methodLevelRbacIsEnforced` proves this). `spring.mvc.problemdetails.enabled` is now
`false` (Boot would back off anyway once a `ResponseEntityExceptionHandler` bean exists). `AuthExceptionHandler`'s
Javadoc now names the global advice as the reason for its `HIGHEST_PRECEDENCE`. Its 400/401/503 tests are unchanged
and pass. The README table has rows for the framework errors and the 500.
**For T10, still not covered:** errors raised outside Spring MVC still go to Boot's `/error`
(`BasicErrorController`), which returns plain JSON with no `errorCode`. An example is an unexpected exception in a
servlet filter. Covering that would mean a custom `ErrorAttributes`/error controller. T10 should word the CLAUDE.md
claim to match (e.g. "errors from controllers, Spring MVC and the security filter chain"), or add a task for it.
Next: T10.
