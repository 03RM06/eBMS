package gov.brgy.ebms.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    private SecretKey signingKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(Long userId, String username, List<String> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtProperties.getAccessTokenExpiryMinutes(), ChronoUnit.MINUTES);

        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("username", username)
            .claim("roles", roles)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(signingKey())
            .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(signingKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long extractUserId(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }

    public String extractUsername(String token) {
        return parseToken(token).get("username", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return parseToken(token).get("roles", List.class);
    }
}
