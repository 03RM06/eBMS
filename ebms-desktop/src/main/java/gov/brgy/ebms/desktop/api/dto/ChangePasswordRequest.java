package gov.brgy.ebms.desktop.api.dto;

public record ChangePasswordRequest(
    String currentPassword,
    String newPassword,
    String confirmPassword
) {}
