package gov.brgy.ebms.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank(message = "Username is required")
    @Size(max = 64, message = "Username must not exceed 64 characters")
    String username,

    @NotBlank(message = "Password is required")
    @Size(max = 128, message = "Password too long")
    String password
) {}
