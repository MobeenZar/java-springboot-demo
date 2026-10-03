package com.example.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login request body.
 * <p>
 * {@code password} is capped at 72 characters because BCrypt ignores input beyond 72 bytes, so longer values would
 * silently match on their prefix; the cap also bounds hashing cost for oversized payloads. {@link #toString()}
 * masks the password so the request can be logged safely.
 *
 * @param userId   login name; the controller trims it before lookup
 * @param password raw password, verified against the stored hash and erased after authentication
 */
public record LoginRequest(
        @NotBlank @Size(max = 100) String userId,
        @NotBlank @Size(max = 72) String password) {

    @Override
    public String toString() {
        return "LoginRequest[userId=" + userId + ", password=***]";
    }
}
