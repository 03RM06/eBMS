package gov.brgy.ebms.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;
    private long accessTokenExpiryMinutes = 15;
    private long refreshTokenExpiryDays = 7;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAccessTokenExpiryMinutes() {
        return accessTokenExpiryMinutes;
    }

    public void setAccessTokenExpiryMinutes(long accessTokenExpiryMinutes) {
        this.accessTokenExpiryMinutes = accessTokenExpiryMinutes;
    }

    public long getRefreshTokenExpiryDays() {
        return refreshTokenExpiryDays;
    }

    public void setRefreshTokenExpiryDays(long refreshTokenExpiryDays) {
        this.refreshTokenExpiryDays = refreshTokenExpiryDays;
    }
}
