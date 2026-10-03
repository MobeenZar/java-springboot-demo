# jwt-oracle-auth

Stateless JWT login API: Spring Boot 3.5, Spring Security 6, Java 17, MyBatis (no JPA), Oracle (`ojdbc11`), jjwt 0.12.
Request flow and error table: @README.md

## Commands

- Build + all tests: `mvn verify` (unit tests + end-to-end MockMvc suite on H2 in Oracle mode; no Oracle needed)
- One test class: `mvn test -Dtest=JwtServiceTest` · one method: `mvn test -Dtest=JwtServiceTest#rejectsTokenSignedWithDifferentKey`
- Run locally: `mvn spring-boot:run`. It needs `ORACLE_JDBC_URL`, `ORACLE_USERNAME`, `ORACLE_PASSWORD`, `JWT_SECRET` (`openssl rand -base64 32`)
- No Maven wrapper. Use the system `mvn` (3.6.3, JDK 17)
- Prefer running the single affected test class while iterating, and finish with `mvn verify` before calling work done

## Architecture

Package by feature under `com.example.auth`:
- `config/`: security filter chain, password encoder, `Clock` bean
- `security/`: entry point / access-denied handlers writing RFC 9457 problem JSON; `security/jwt/` issues and validates tokens
- `user/`: `UserMapper` (MyBatis) → `UserAccount` (row) → `SecurityUser` (`UserDetails`)
- `web/`: controllers, request/response records, `@RestControllerAdvice`

Key decisions (do not change without asking):
- **Stateless**: no HTTP session, no CSRF, no DB hit per request. Authorities come from the token's `roles` claim
- **Existing Oracle schema**: the app never creates or migrates tables. `spring.sql.init.mode: never` in main config
- **Login failures are indistinguishable**: unknown user and wrong password both return `INVALID_CREDENTIALS`. Only an
  expired password (raised by `DaoAuthenticationProvider` *after* the password is verified) returns `PASSWORD_EXPIRED`
- Errors are `ProblemDetail` with an `errorCode` property, never stack traces or exception messages

## Gotchas

- `JwtAuthenticationFilter` is deliberately **not** a Spring bean. Registering it as one makes Boot add it to the
  servlet chain too, so it runs twice. It is instantiated in `SecurityConfig`.
- Get "now" from the injected `java.time.Clock`, never `Instant.now()`/`LocalDateTime.now()` in production code
  (tests fix the clock for expiry checks).
- Roles in the DB may be `ADMIN` or `ROLE_ADMIN`. `SecurityUser` normalises both to `ROLE_ADMIN`.
- Password hashes may be stored with or without a `{bcrypt}` prefix. `LoginRequest.password` is capped at 72 chars (BCrypt limit).
- MyBatis is configured with `jdbc-type-for-null: 'NULL'` because Oracle rejects untyped null binds. Still give every
  `#{param}` an explicit `jdbcType`.
- H2 in Oracle mode is not Oracle. Avoid SQL that only one of them accepts (e.g. `MERGE` variants, `CONNECT BY`).
  If you must, flag it so it gets tested against a real Oracle.
- Test schema lives in `src/test/resources/test-schema.sql` and must mirror the columns `UserMapper.xml` selects.

## Workflow

- For changes touching security config, the JWT filter, or login flow: plan first, and add or extend an
  `AuthIntegrationTest` case that proves the HTTP behaviour (status, `errorCode`, `WWW-Authenticate`).
- Never put real secrets in `application*.yml`. Use `${ENV_VAR}` placeholders. The key in `application-test.yml` is test-only.
- Branch off `main`. Keep commits focused, with imperative-mood messages.

Detailed Java/Spring, testing and MyBatis conventions live in `.claude/rules/` and load when matching files are edited.
