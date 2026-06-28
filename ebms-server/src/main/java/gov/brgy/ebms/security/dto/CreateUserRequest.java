package gov.brgy.ebms.security.dto;

import jakarta.validation.constraints.*;

import java.util.Set;

public record CreateUserRequest(
    @NotBlank
    @Size(min = 3, max = 64)
    String username,

    @Email
    @Size(max = 128)
    String email,

    @NotBlank
    @Size(min = 8, max = 128)
    String password,

    @NotBlank
    @Size(max = 160)
    String fullName,

    @NotEmpty
    Set<String> roles
) {}
