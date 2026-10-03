package com.example.auth;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    private static final String PASSWORD = "S3cure!pass";

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
        LocalDateTime now = LocalDateTime.now();

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
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    void tamperedTokenIsUnauthorized() throws Exception {
        String token = loginAndGetToken("alice");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")));
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
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")));
    }

    @Test
    void invalidTokenIsRejectedEvenOnPublicEndpoint() throws Exception {
        mockMvc.perform(loginRequest("alice", PASSWORD).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")));
    }

    @Test
    void urlBasedRbacAllowsAdminAndForbidsUser() throws Exception {
        mockMvc.perform(get("/api/admin/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + loginAndGetToken("alice")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + loginAndGetToken("bob")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void methodLevelRbacIsEnforced() throws Exception {
        mockMvc.perform(get("/api/reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + loginAndGetToken("carol")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + loginAndGetToken("bob")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    private String loginAndGetToken(String userId) throws Exception {
        String body = mockMvc.perform(loginRequest(userId, PASSWORD))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("access_token").asText();
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
}
