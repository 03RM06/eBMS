package gov.brgy.ebms.usermgmt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateUserRequest(
    @NotBlank @Size(max = 64) String username,
    @Size(max = 128) String email,
    @NotBlank @Size(max = 160) String fullName,
    @NotBlank @Size(min = 10, max = 128) String password,
    @NotNull List<Long> roleIds
) {}
