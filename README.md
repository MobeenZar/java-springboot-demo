# java-springboot-demo — stateless JWT auth on Oracle + MyBatis

Spring Boot 3.5 / Spring Security 6 / Java 17 / MyBatis / Oracle (`ojdbc11`) / JWT (jjwt 0.12).

## Flow

1. `POST /api/auth/login` with `{"userId": "...", "password": "..."}`.
2. `AuthenticationManager` → `DaoAuthenticationProvider` → `DatabaseUserDetailsService` → `UserMapper.findByUserId`
   (one query: user, password hash, password expiry date, roles).
3. The provider verifies the password, **then** checks `SecurityUser.isCredentialsNonExpired()`.
4. On success the response is `{"access_token": "...", "token_type": "Bearer", "expires_in": 900}`.
5. Later requests send `Authorization: Bearer <token>`; `JwtAuthenticationFilter` validates it and fills the
   `SecurityContext` from the token's claims (no DB hit per request, no HTTP session).

Each error below is an RFC 9457 problem (`application/problem+json`) with a stable `errorCode` property.

| Situation | Status | `errorCode` | Notes |
|---|---|---|---|
| Unknown user / wrong password | 401 | `INVALID_CREDENTIALS` | Identical for both |
| Correct password, but expired | 401 | `PASSWORD_EXPIRED` | |
| Missing token on protected URL | 401 | `AUTHENTICATION_REQUIRED` | `WWW-Authenticate: Bearer` |
| Invalid / expired / tampered token | 401 | `INVALID_TOKEN` | `WWW-Authenticate: Bearer error="invalid_token"`, also on public URLs |
| Authenticated but missing role | 403 | `ACCESS_DENIED` | |

## Adapting to your schema

Edit `src/main/resources/mapper/UserMapper.xml`. It assumes:

- `APP_USERS(USER_ID, PASSWORD_HASH, PASSWORD_EXPIRY_DATE)` — `NULL` expiry = never expires
- `APP_USER_ROLES(USER_ID, ROLE_NAME)` — `ADMIN` and `ROLE_ADMIN` are both accepted

Keep the column aliases and the result map keeps working. Hashes are BCrypt (with or without a `{bcrypt}` prefix);
change `SecurityConfig.passwordEncoder()` for a different algorithm.

## Running

```bash
export ORACLE_JDBC_URL=jdbc:oracle:thin:@//db-host:1521/SERVICE
export ORACLE_USERNAME=app_user ORACLE_PASSWORD=...
export JWT_SECRET=$(openssl rand -base64 32)
mvn spring-boot:run
```

`mvn verify` runs unit tests plus an end-to-end suite against H2 in Oracle mode (test-only schema in
`src/test/resources`); nothing is created in Oracle.
