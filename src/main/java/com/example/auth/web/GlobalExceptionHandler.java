package com.example.auth.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Gives errors raised by Spring MVC itself (unknown path, unsupported method or media type, ...) and unexpected
 * exceptions the same problem shape as every other error: a stable {@code errorCode} and a fixed {@code detail}.
 * <p>
 * Spring's own {@code detail} texts echo request data (path, method, content type), so they are replaced. Headers
 * Spring sets, such as {@code Allow} on a 405, are kept. The {@code errorCode} is {@code INVALID_REQUEST} for 400
 * (matching login), {@code INTERNAL_ERROR} for 500 and the {@link HttpStatus} name otherwise.
 * <p>
 * Unlike the other advice this one is deliberately not scoped: a 404 or 405 is raised before any controller is
 * chosen, and a scoped advice never applies without one. It runs last, so {@link AuthExceptionHandler} still answers
 * login errors. Being a {@link ResponseEntityExceptionHandler}, it also makes Boot skip its own
 * {@code ProblemDetailsExceptionHandler}.
 * <p>
 * Security exceptions thrown from controllers (e.g. a {@code @PreAuthorize} denial) are rethrown untouched so they
 * reach the filter chain's entry point and access-denied handler instead of the catch-all 500.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    void rethrowSecurityException(Exception ex) throws Exception {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return createResponseEntity(ProblemDetail.forStatus(status), new HttpHeaders(), status, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
                                                          HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            problem.setTitle(status != null ? status.getReasonPhrase() : null);
            problem.setDetail(detailFor(statusCode));
            problem.setProperty(AuthExceptionHandler.ERROR_CODE, errorCodeFor(statusCode, status));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static String errorCodeFor(HttpStatusCode statusCode, HttpStatus status) {
        if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
            return "INVALID_REQUEST";
        }
        if (statusCode.value() == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            return "INTERNAL_ERROR";
        }
        return status != null ? status.name() : "HTTP_" + statusCode.value();
    }

    private static String detailFor(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 400 -> "The request is missing or invalid";
            case 404 -> "No resource exists at this path";
            case 405 -> "The request method is not supported for this resource";
            case 406 -> "The requested response media type is not supported";
            case 415 -> "The request content type is not supported";
            case 500 -> "An unexpected error occurred";
            default -> statusCode.is5xxServerError()
                    ? "The service could not process the request"
                    : "The request could not be processed";
        };
    }
}
