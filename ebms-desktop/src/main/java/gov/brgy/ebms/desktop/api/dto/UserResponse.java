package gov.brgy.ebms.desktop.api.dto;

import java.util.List;

public record UserResponse(
    Long id,
    String username,
    String email,
    String fullName,
    String preferredLocale,
    boolean enabled,
    List<String> roles
) {}
