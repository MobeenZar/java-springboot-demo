package com.example.auth.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps login failures to problem responses with a stable {@code errorCode}.
 * <p>
 * Every authentication failure produces the same generic 401 message so callers cannot probe which user ids
 * exist, with one exception: an expired password is reported explicitly, because {@code DaoAuthenticationProvider}
 * only raises it after the password was verified, and the client needs it to route the user to a
 * password-change flow.
 * <p>
 * {@code InternalAuthenticationServiceException} is not a credentials problem: {@code DaoAuthenticationProvider}
 * wraps any unexpected failure while loading the user (typically the database being unreachable) in it. It gets a
 * 503 and an {@code error} log with the cause, so an outage neither looks like users mistyping passwords nor
 * leaves no trace. The cause never goes into the response.
 * <p>
 * A body that fails validation or cannot be parsed gets a generic 400 that echoes no field values or parser
 * output. Boot's own {@code ProblemDetailsExceptionHandler} also handles those exceptions and is ordered, and
 * Spring picks the first matching advice by order rather than the most specific handler. This advice therefore
 * runs first; it only applies to {@link AuthController}, so other controllers keep Boot's defaults.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
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

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ProblemDetail handleInvalidRequest(Exception ex) {
        log.debug("Invalid login request: {}", ex.getClass().getSimpleName());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "The request body is missing or invalid");
        problem.setTitle("Invalid request");
        problem.setProperty(ERROR_CODE, "INVALID_REQUEST");
        return problem;
    }

    @ExceptionHandler(InternalAuthenticationServiceException.class)
    ProblemDetail handleInternalFailure(InternalAuthenticationServiceException ex) {
        log.error("Login failed due to an internal error", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The service is temporarily unavailable, please try again later");
        problem.setTitle("Service unavailable");
        problem.setProperty(ERROR_CODE, "SERVICE_UNAVAILABLE");
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
