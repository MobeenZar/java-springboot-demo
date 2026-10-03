---
paths:
  - "src/test/**"
---

# Testing conventions

- JUnit 5 + AssertJ (`assertThat`, `assertThatThrownBy`). Hamcrest only inside MockMvc result matchers.
- Test classes and methods are package-private. Method names describe behaviour in camelCase:
  `rejectsTokenSignedWithDifferentKey`, not `test1` or `testParse`.
- Prefer plain unit tests that construct the class directly (see `JwtServiceTest`). Use a fixed
  `Clock.fixed(...)` for anything time-dependent. Never `Thread.sleep`.
- HTTP behaviour is verified end-to-end in `AuthIntegrationTest` (`@SpringBootTest` + `@AutoConfigureMockMvc`
  + `@ActiveProfiles("test")`). Assert status, `errorCode`, and relevant headers such as `WWW-Authenticate` and `Cache-Control`.
- Seed data per test with `JdbcTemplate` in `@BeforeEach` (delete then insert). Don't rely on test execution order.
- Use `@WebMvcTest` / `@MybatisTest` slices for focused tests of one layer. Avoid adding more full-context tests than needed.
- Only mock what you don't own or what is slow. Don't mock `JwtService` or `PasswordEncoder` in integration tests.
- Every bug fix starts with a failing test that reproduces it.
- Never weaken an assertion or `@Disabled` a test to make the build pass. Fix the cause or ask.
