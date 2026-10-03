package com.example.auth.security.jwt;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * Issues and validates short-lived, HMAC-signed (HS256) access tokens.
 * <p>
 * Token layout: {@code sub} = user id, {@code roles} = granted authorities (e.g. {@code ROLE_ADMIN}),
 * plus {@code iss}, {@code iat}, {@code exp} and a unique {@code jti}.
 */
@Service
public class JwtService {

    static final String ROLES_CLAIM = "roles";

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey signingKey;
    private final JwtParser parser;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        // Keys.hmacShaKeyFor rejects keys shorter than 256 bits, failing fast at startup.
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        this.parser = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .clockSkewSeconds(properties.clockSkew().toSeconds())
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    public String generateAccessToken(String userId, Collection<? extends GrantedAuthority> authorities) {
        Instant now = clock.instant();
        List<String> roles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(properties.issuer())
                .subject(userId)
                .claim(ROLES_CLAIM, roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Verifies signature, issuer and expiry, and returns the token's user id and roles.
     *
     * @throws InvalidJwtException if the token is malformed, tampered with, expired or otherwise invalid
     */
    public AccessTokenClaims parseAndValidate(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            String userId = claims.getSubject();
            if (userId == null || userId.isBlank()) {
                throw new InvalidJwtException("Token has no subject");
            }
            Date expiration = claims.getExpiration();
            if (expiration == null) {
                throw new InvalidJwtException("Token has no expiration");
            }
            return new AccessTokenClaims(userId, extractRoles(claims), expiration.toInstant());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidJwtException("Invalid access token", ex);
        }
    }

    public long accessTokenTtlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }

    private static List<String> extractRoles(Claims claims) {
        Object raw = claims.get(ROLES_CLAIM);
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof Collection<?> values)) {
            throw new InvalidJwtException("Claim '" + ROLES_CLAIM + "' must be an array");
        }
        return values.stream().map(String::valueOf).toList();
    }

    public record AccessTokenClaims(String userId, List<String> roles, Instant expiresAt) {
    }
}
