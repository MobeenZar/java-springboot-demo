package com.example.auth.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

class SecurityUserTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Test
    void credentialsExpireExactlyAtExpiryTimestamp() {
        assertThat(user(NOW.plusSeconds(1)).isCredentialsNonExpired()).isTrue();
        assertThat(user(NOW).isCredentialsNonExpired()).isFalse();
        assertThat(user(NOW.minusDays(1)).isCredentialsNonExpired()).isFalse();
    }

    @Test
    void nullExpiryNeverExpires() {
        assertThat(user(null).isCredentialsNonExpired()).isTrue();
    }

    @Test
    void normalisesRolesToSpringConvention() {
        UserAccount account = account(null, " admin ", "ROLE_USER", "ADMIN", "", null);

        List<String> authorities = new SecurityUser(account, CLOCK).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorities).containsExactly("ROLE_ADMIN", "ROLE_USER");
    }

    private static SecurityUser user(LocalDateTime expiresAt) {
        return new SecurityUser(account(expiresAt), CLOCK);
    }

    private static UserAccount account(LocalDateTime expiresAt, String... roles) {
        UserAccount account = new UserAccount();
        account.setUserId("alice");
        account.setPasswordHash("hash");
        account.setPasswordExpiresAt(expiresAt);
        account.setRoles(new LinkedHashSet<>(Arrays.asList(roles)));
        return account;
    }
}
