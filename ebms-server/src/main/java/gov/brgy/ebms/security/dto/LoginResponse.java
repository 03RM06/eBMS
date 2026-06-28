package gov.brgy.ebms.security.dto;

import java.util.List;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    Long userId,
    String username,
    String fullName,
    List<String> roles,
    boolean requiresPasswordChange
) {}
