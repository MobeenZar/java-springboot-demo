---
paths:
  - "src/main/java/**/*.java"
---

# Java & Spring Boot conventions

## Design
- Constructor injection only, with `private final` fields. No `@Autowired` on fields or setters in production code.
  Leave out `@Autowired` on single-constructor classes.
- `@Configuration(proxyBeanMethods = false)` for configuration classes.
- Bind settings with `@ConfigurationProperties` records plus `@Validated` and Bean Validation constraints, not
  scattered `@Value`. Document each component with `@param` in the record's Javadoc (see `JwtProperties`).
- New config keys go under `app.*`, with env-var placeholders and safe defaults in `application.yml`.
- Keep controllers thin: validate input (`@Valid`), delegate, shape the response. Business logic lives in services.
- Use records for request/response DTOs and value types. Don't expose `UserAccount` or other persistence types from controllers.
- Classes and members get the narrowest visibility that works. Package-private is fine for constants shared with tests.
- Return `Optional` from lookups that can miss, never `null`. Don't use `Optional` for fields or parameters.
- Prefer immutable collections (`List.of`, `stream().toList()`).

## Errors & HTTP
- Map exceptions to `ProblemDetail` in a `@RestControllerAdvice` scoped with `assignableTypes`/`basePackages`.
  Include a stable `errorCode` property. Never put exception messages, SQL or stack traces in responses.
- Security failures (401/403) are written by `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler` through
  `ProblemDetailWriter`, not by controller advice (filter-chain exceptions never reach it).
- Define custom exceptions as unchecked and specific (e.g. `InvalidJwtException`). Wrap third-party exceptions at the boundary,
  keeping the cause.
- Use `ResponseEntity` only when you need headers/status beyond the default. Token responses must send `Cache-Control: no-store`.

## Security
- Authorization: URL rules in `SecurityConfig` for coarse paths, `@PreAuthorize("hasRole(...)")` for per-method rules.
  Every new endpoint needs an explicit decision. `anyRequest().authenticated()` is the fallback, not the plan.
- Never log passwords, hashes, or tokens. Override `toString()` on records/classes that hold them (see `LoginRequest`).
- Log with SLF4J placeholders (`log.debug("x {}", y)`), never string concatenation. Use `debug` for expected auth failures.
- Read the current user from `@AuthenticationPrincipal` or `Authentication`, not from request parameters.

## Style
- Import groups, separated by blank lines: static imports, `java.*`/`javax.*`, `jakarta.*`, other packages
  (`com.*`, `io.*`), then `org.*`. No wildcard imports.
- 4-space indent, 120-column lines, 8-space continuation indent.
- Javadoc on public types and non-obvious public methods: explain *why* and contract (`@throws`, empty/null
  semantics), not what the code literally does.
- Use `var` only when the type is obvious from the right-hand side.
