package gov.brgy.ebms.usermgmt.dto;

import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
    @Size(max = 160) String fullName,
    @Size(max = 128) String email,
    Boolean enabled
) {}
