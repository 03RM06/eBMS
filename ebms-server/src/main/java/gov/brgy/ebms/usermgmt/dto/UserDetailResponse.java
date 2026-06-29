package gov.brgy.ebms.usermgmt.dto;

import gov.brgy.ebms.security.entity.Role;
import gov.brgy.ebms.security.entity.User;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

public record UserDetailResponse(
    Long id,
    String username,
    String email,
    String fullName,
    Boolean enabled,
    Set<String> roles,
    int failedLoginAttempts,
    LocalDateTime lockedUntil,
    boolean forcedPasswordChange,
    LocalDateTime createdAt
) {
    public static UserDetailResponse from(User u) {
        return new UserDetailResponse(
            u.getId(), u.getUsername(), u.getEmail(), u.getFullName(),
            u.getEnabled(),
            u.getRoles().stream().map(Role::getCode).collect(Collectors.toSet()),
            u.getFailedLoginAttempts(), u.getLockedUntil(),
            u.isForcedPasswordChange(), u.getCreatedAt()
        );
    }
}
