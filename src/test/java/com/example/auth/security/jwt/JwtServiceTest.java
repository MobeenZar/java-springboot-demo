package com.example.auth.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class JwtServiceTest {

    private static final String SECRET = "3q2+7wABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhs=";
    private static final String OTHER_SECRET = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void roundTripsUserIdAndRoles() {
        JwtService service = service(SECRET, "issuer", NOW);

        String token = service.generateAccessToken("alice",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER")));
        JwtService.AccessTokenClaims claims = service.parseAndValidate(token);

        assertThat(claims.userId()).isEqualTo("alice");
        assertThat(claims.roles()).containsExactly("ROLE_ADMIN", "ROLE_USER");
        assertThat(claims.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void rejectsExpiredTokenBeyondClockSkew() {
        String token = service(SECRET, "issuer", NOW).generateAccessToken("alice", List.of());

        JwtService withinSkew = service(SECRET, "issuer", NOW.plus(Duration.ofMinutes(15)).plusSeconds(20));
        assertThat(withinSkew.parseAndValidate(token).userId()).isEqualTo("alice");

        JwtService afterExpiry = service(SECRET, "issuer", NOW.plus(Duration.ofMinutes(16)));
        assertThatThrownBy(() -> afterExpiry.parseAndValidate(token)).isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        String token = service(OTHER_SECRET, "issuer", NOW).generateAccessToken("alice", List.of());

        assertThatThrownBy(() -> service(SECRET, "issuer", NOW).parseAndValidate(token))
                .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void rejectsTokenFromDifferentIssuer() {
        String token = service(SECRET, "someone-else", NOW).generateAccessToken("alice", List.of());

        assertThatThrownBy(() -> service(SECRET, "issuer", NOW).parseAndValidate(token))
                .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> service(SECRET, "issuer", NOW).parseAndValidate("not.a.jwt"))
                .isInstanceOf(InvalidJwtException.class);
        assertThatThrownBy(() -> service(SECRET, "issuer", NOW).parseAndValidate(""))
                .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void rejectsTokenWithoutExpiration() {
        String token = Jwts.builder()
                .issuer("issuer")
                .subject("alice")
                .issuedAt(Date.from(NOW))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> service(SECRET, "issuer", NOW).parseAndValidate(token))
                .isInstanceOf(InvalidJwtException.class)
                .hasMessage("Token has no expiration");
    }

    @Test
    void refusesKeysShorterThan256Bits() {
        assertThatThrownBy(() -> service("c2hvcnQta2V5", "issuer", NOW))
                .isInstanceOf(WeakKeyException.class);
    }

    private static JwtService service(String secret, String issuer, Instant now) {
        JwtProperties properties = new JwtProperties(secret, issuer, Duration.ofMinutes(15), Duration.ofSeconds(30));
        return new JwtService(properties, Clock.fixed(now, ZoneOffset.UTC));
    }
}
