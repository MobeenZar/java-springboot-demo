package com.example.auth.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps login failures to 401 problem responses.
 * <p>
 * Every failure produces the same generic message so callers cannot probe which user ids exist, with one
 * exception: an expired password is reported explicitly, because {@code DaoAuthenticationProvider} only
 * raises it after the password was verified, and the client needs it to route the user to a
 * password-change flow.
 */
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthExceptionHandler.class);

    static final String ERROR_CODE = "errorCode";

    @ExceptionHandler(CredentialsExpiredException.class)
    ProblemDetail handleCredentialsExpired(CredentialsExpiredException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Your password has expired and must be changed");
        problem.setTitle("Password expired");
        problem.setProperty(ERROR_CODE, "PASSWORD_EXPIRED");
        return problem;
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthenticationFailure(AuthenticationException ex) {
        log.debug("Login failed: {}", ex.getClass().getSimpleName());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Invalid user ID or password");
        problem.setTitle("Authentication failed");
        problem.setProperty(ERROR_CODE, "INVALID_CREDENTIALS");
        return problem;
    }
}
