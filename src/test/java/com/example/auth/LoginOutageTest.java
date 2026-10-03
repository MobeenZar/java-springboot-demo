package com.example.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auth.user.UserMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Login while the database is unreachable.
 * <p>
 * Kept apart from {@link AuthIntegrationTest} so that one keeps running against the real (H2) database: an outage
 * cannot be simulated there without breaking the shared schema, so {@link UserMapper} is mocked here instead.
 * <p>
 * The mock gives this class its own application context, but the in-memory database is shared and outlives contexts
 * ({@code DB_CLOSE_DELAY=-1}), so re-running {@code test-schema.sql} would fail on existing tables. With the mapper
 * mocked no table is read, so schema initialisation is switched off for this context.
 */
@SpringBootTest(properties = "spring.sql.init.mode=never")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class LoginOutageTest {

    private static final String CAUSE_MESSAGE =
            "Connection refused: jdbc:oracle:thin:@db-secret-host:1521/PROD while running SELECT ... FROM APP_USERS";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserMapper userMapper;

    @BeforeEach
    void databaseIsDown() {
        when(userMapper.findByUserId(anyString())).thenThrow(new DataAccessResourceFailureException(CAUSE_MESSAGE));
    }

    @Test
    void databaseOutageDuringLoginIsServiceUnavailable() throws Exception {
        login()
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.title").value("Service unavailable"))
                .andExpect(jsonPath("$.detail").value("The service is temporarily unavailable, please try again later"))
                .andExpect(content().string(not(containsString("db-secret-host"))))
                .andExpect(content().string(not(containsString("APP_USERS"))));
    }

    @Test
    void databaseOutageIsLoggedAtErrorWithCause(CapturedOutput output) throws Exception {
        login();

        assertThat(output.getOut()).containsPattern("ERROR.*AuthExceptionHandler");
        assertThat(output.getOut()).contains(CAUSE_MESSAGE);
    }

    private ResultActions login() throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"alice\",\"password\":\"S3cure!pass\"}"));
    }
}
