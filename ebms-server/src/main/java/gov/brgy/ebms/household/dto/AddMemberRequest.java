package gov.brgy.ebms.household.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddMemberRequest(
    @NotNull(message = "residentId is required") Long residentId,
    @NotBlank(message = "relationship is required") String relationship
) {}
