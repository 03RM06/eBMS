package gov.brgy.ebms.security.dto;

import gov.brgy.ebms.security.entity.User;

import java.util.List;

public record UserResponse(
    Long id,
    String username,
    String email,
    String fullName,
    String preferredLocale,
    boolean enabled,
    List<String> roles
) {
    public static UserResponse from(User user) {
        List<String> roleCodes = user.getRoles().stream()
            .map(r -> r.getCode())
            .toList();
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getFullName(),
            user.getPreferredLocale(),
            Boolean.TRUE.equals(user.getEnabled()),
            roleCodes
        );
    }
}
