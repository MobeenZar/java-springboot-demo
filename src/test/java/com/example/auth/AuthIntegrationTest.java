package com.example.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.example.auth.security.jwt.JwtProperties;
import com.example.auth.security.jwt.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AuthIntegrationTest {

    private static final String PASSWORD = "S3cure!pass";
    private static final String FAILURE_MESSAGE = "ORA-00942: table or view does not exist: SELECT * FROM APP_SECRETS";
    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private Clock clock;

    @BeforeEach
    void seedUsers() {
        jdbcTemplate.update("DELETE FROM APP_USER_ROLES");
        jdbcTemplate.update("DELETE FROM APP_USERS");

        // Raw BCrypt hash without {id} prefix, as a legacy table would typically store it.
        String legacyHash = passwordEncoder.encode(PASSWORD).replace("{bcrypt}", "");
        LocalDateTime now = LocalDateTime.now(clock);

        insertUser("alice", legacyHash, now.plusDays(30), "ADMIN", "user");
        insertUser("bob", passwordEncoder.encode(PASSWORD), now.plusDays(30), "ROLE_USER");
        insertUser("carol", legacyHash, now.plusDays(30), "MANAGER");
        insertUser("expired", legacyHash, now.minusDays(1), "USER");
        insertUser("noroles", legacyHash, null);
    }

    @Test
    void loginReturnsBearerTokenUsableOnProtectedEndpoint() throws Exception {
        String token = loginAndGetToken("alice");

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("alice"))
                .andExpect(jsonPath("$.roles", containsInAnyOrder("ROLE_ADMIN", "ROLE_USER")));
    }

    @Test
    void loginResponseHasTokenMetadataAndIsNotCacheable() throws Exception {
        mockMvc.perform(loginRequest("alice", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }

    @Test
    void wrongPasswordIsRejectedGenerically() throws Exception {
        mockMvc.perform(loginRequest("alice", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void unknownUserIsIndistinguishableFromWrongPassword() throws Exception {
        mockMvc.perform(loginRequest("nobody", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void expiredPasswordIsReportedOnlyAfterCorrectPassword() throws Exception {
        mockMvc.perform(loginRequest("expired", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("PASSWORD_EXPIRED"));

        mockMvc.perform(loginRequest("expired", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void nullExpiryMeansPasswordNeverExpiresAndUserWithoutRolesCanLogIn() throws Exception {
        String token = loginAndGetToken("noroles");

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isEmpty());
    }

    @Test
    void blankCredentialsAreBadRequest() throws Exception {
        mockMvc.perform(loginRequest("", ""))
                .andExpect(invalidRequest());
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":"))
                .andExpect(invalidRequest());
    }

    @Test
    void passwordLongerThanBcryptLimitIsBadRequest() throws Exception {
        mockMvc.perform(loginRequest("alice", "a".repeat(73)))
                .andExpect(invalidRequest());
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.title").value("Authentication required"));
    }

    @Test
    void tamperedTokenIsUnauthorized() throws Exception {
        String token = loginAndGetToken("alice");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")))
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.title").value("Invalid token"));
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        // Issue the token far enough back that it is past exp plus the clock skew, whatever the configured TTL.
        Instant issuedAt = clock.instant()
                .minus(jwtProperties.accessTokenTtl())
                .minus(jwtProperties.clockSkew())
                .minus(Duration.ofHours(1));
        JwtService pastJwtService = new JwtService(jwtProperties, Clock.fixed(issuedAt, ZoneOffset.UTC));
        String expiredToken = pastJwtService.generateAccessToken("alice", List.of());

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")))
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.title").value("Invalid token"));
    }

    @Test
    void invalidTokenIsRejectedEvenOnPublicEndpoint() throws Exception {
        mockMvc.perform(loginRequest("alice", PASSWORD).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")))
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.title").value("Invalid token"));
    }

    @Test
    void urlBasedRbacAllowsAdminAndForbidsUser() throws Exception {
        mockMvc.perform(get("/api/admin/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + loginAndGetToken("alice")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + loginAndGetToken("bob")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.title").value("Access denied"));
    }

    @Test
    void methodLevelRbacIsEnforced() throws Exception {
        mockMvc.perform(get("/api/reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + loginAndGetToken("carol")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + loginAndGetToken("bob")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.title").value("Access denied"));
    }

    @Test
    void unknownPathIsNotFound() throws Exception {
        mockMvc.perform(get("/api/nope").header(HttpHeaders.AUTHORIZATION, "Bearer " + loginAndGetToken("alice")))
                .andExpect(frameworkProblem(HttpStatus.NOT_FOUND, "NOT_FOUND", "No resource exists at this path"));
    }

    @Test
    void unsupportedMethodIsMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + loginAndGetToken("alice")))
                .andExpect(frameworkProblem(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                        "The request method is not supported for this resource"))
                .andExpect(header().string(HttpHeaders.ALLOW, "GET"));
    }

    @Test
    void unsupportedContentTypeOnLoginIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("alice:" + PASSWORD))
                .andExpect(frameworkProblem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                        "The request content type is not supported"));
    }

    @Test
    void unacceptableMediaTypeIsRejected() throws Exception {
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + loginAndGetToken("alice"))
                        .accept(MediaType.APPLICATION_XML))
                .andExpect(frameworkProblem(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE",
                        "The requested response media type is not supported"));
    }

    @Test
    void unexpectedExceptionIsInternalErrorWithoutDetails(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/failure").header(HttpHeaders.AUTHORIZATION, "Bearer " + loginAndGetToken("alice")))
                .andExpect(frameworkProblem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                        "An unexpected error occurred"))
                .andExpect(content().string(not(containsString("APP_SECRETS"))));

        assertThat(output).contains("ERROR").contains("GlobalExceptionHandler").contains(FAILURE_MESSAGE);
    }

    private String loginAndGetToken(String userId) throws Exception {
        String body = mockMvc.perform(loginRequest(userId, PASSWORD))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("access_token").asText();
    }

    /** The fixed detail proves no field value, exception message or parser output is echoed back. */
    private static ResultMatcher invalidRequest() {
        return ResultMatcher.matchAll(
                status().isBadRequest(),
                content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON),
                jsonPath("$.errorCode").value("INVALID_REQUEST"),
                jsonPath("$.title").value("Invalid request"),
                jsonPath("$.detail").value("The request body is missing or invalid"));
    }

    /** The fixed detail proves no path, method, media type or exception message is echoed back. */
    private static ResultMatcher frameworkProblem(HttpStatus status, String errorCode, String detail) {
        return ResultMatcher.matchAll(
                status().is(status.value()),
                content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON),
                jsonPath("$.status").value(status.value()),
                jsonPath("$.errorCode").value(errorCode),
                jsonPath("$.title").value(status.getReasonPhrase()),
                jsonPath("$.detail").value(detail));
    }

    private MockHttpServletRequestBuilder loginRequest(String userId, String password) throws Exception {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new Credentials(userId, password)));
    }

    private void insertUser(String userId, String hash, LocalDateTime expiry, String... roles) {
        jdbcTemplate.update("INSERT INTO APP_USERS (USER_ID, PASSWORD_HASH, PASSWORD_EXPIRY_DATE) VALUES (?, ?, ?)",
                userId, hash, expiry == null ? null : Timestamp.valueOf(expiry));
        for (String role : roles) {
            jdbcTemplate.update("INSERT INTO APP_USER_ROLES (USER_ID, ROLE_NAME) VALUES (?, ?)", userId, role);
        }
    }

    private record Credentials(String userId, String password) {
    }

    /**
     * Fixes "now" for seed data, token issuing and expiry checks. Added as a {@code @Primary} bean under its own name
     * because bean-definition overriding is off, so {@code ClockConfig.clock} can't be replaced by name.
     */
    @TestConfiguration(proxyBeanMethods = false)
    @Import(FailingController.class)
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }

    /**
     * Stands in for a bug in a controller, since no production endpoint throws an unexpected exception. Nested in the
     * test class, so component scanning skips it and only this context registers it (through {@code @Import}).
     */
    @RestController
    static class FailingController {

        @GetMapping("/test/failure")
        String fail() {
            throw new IllegalStateException(FAILURE_MESSAGE);
        }
    }
}
