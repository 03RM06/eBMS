package gov.brgy.ebms.security;

import gov.brgy.ebms.security.jwt.JwtProperties;
import gov.brgy.ebms.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-key-for-unit-tests-only-256-bits-long-padded-here-x");
        props.setAccessTokenExpiryMinutes(15);
        props.setRefreshTokenExpiryDays(7);
        jwtService = new JwtService(props);
    }

    @Test
    void generateAndParseToken_shouldRoundTrip() {
        String token = jwtService.generateAccessToken(42L, "testuser", List.of("STAFF"));

        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
        assertThat(jwtService.extractUsername(token)).isEqualTo("testuser");
        assertThat(jwtService.extractRoles(token)).containsExactly("STAFF");
    }

    @Test
    void isTokenValid_shouldReturnFalseForGarbage() {
        assertThat(jwtService.isTokenValid("not.a.valid.token")).isFalse();
    }

    @Test
    void isTokenValid_shouldReturnFalseForEmptyString() {
        assertThat(jwtService.isTokenValid("")).isFalse();
    }
}
