package gov.brgy.ebms.desktop.core;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Arrays;
import java.util.List;

public class Session {

    private static final Session INSTANCE = new Session();

    private String accessToken;
    private String refreshToken;
    private Long userId;
    private String username;
    private String fullName;
    private List<String> roles = List.of();
    private final ObjectProperty<String> localeProperty = new SimpleObjectProperty<>("en");

    private Session() {}

    public static Session get() {
        return INSTANCE;
    }

    public boolean hasAnyRole(String... roleNames) {
        return Arrays.stream(roleNames).anyMatch(r -> roles.contains("ROLE_" + r));
    }

    public void update(String access, String refresh, Long uid,
                       String uname, String fname, List<String> r) {
        this.accessToken = access;
        this.refreshToken = refresh;
        this.userId = uid;
        this.username = uname;
        this.fullName = fname;
        this.roles = r != null ? r : List.of();
    }

    public void clear() {
        accessToken = null;
        refreshToken = null;
        userId = null;
        username = null;
        fullName = null;
        roles = List.of();
    }

    // Getters
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public List<String> getRoles() { return roles; }

    // Locale
    public String getLocale() { return localeProperty.get(); }
    public void setLocale(String locale) { localeProperty.set(locale); }
    public ObjectProperty<String> localeProperty() { return localeProperty; }
}
