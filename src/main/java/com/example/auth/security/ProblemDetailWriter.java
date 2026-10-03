package com.example.auth.security;

import java.io.IOException;
import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/** Writes RFC 9457 problem responses from inside the filter chain, where MVC advice does not apply. */
@Component
public class ProblemDetailWriter {

    private static final String ERROR_CODE = "errorCode";

    private final ObjectMapper objectMapper;

    public ProblemDetailWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Writes the problem body with the given status.
     *
     * @param errorCode stable, machine-readable code clients branch on (the {@code errorCode} property)
     * @param title     short human-readable summary of the error type
     * @param detail    generic explanation; never an exception message, since it is sent to the client
     */
    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                      String errorCode, String title, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty(ERROR_CODE, errorCode);
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
