package com.example.auth.web;

import jakarta.validation.Valid;

import com.example.auth.security.jwt.JwtService;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public login endpoint that exchanges a user id and password for a short-lived bearer token.
 * <p>
 * Authentication failures are deliberately not caught here: they propagate to {@link AuthExceptionHandler}, which
 * keeps unknown-user and wrong-password responses identical. The token is sent with {@code Cache-Control: no-store}
 * so no intermediary caches it.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /**
     * Verifies the user id and password against the database and returns a short-lived access token.
     * Failures are translated to 401 responses by {@link AuthExceptionHandler}.
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.userId().trim(), request.password()));

        String token = jwtService.generateAccessToken(authentication.getName(), authentication.getAuthorities());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(TokenResponse.bearer(token, jwtService.accessTokenTtlSeconds()));
    }
}
