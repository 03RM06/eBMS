package gov.brgy.ebms.household.dto;

import jakarta.validation.constraints.NotNull;

public record SetHeadRequest(
    @NotNull(message = "residentId is required") Long residentId
) {}
