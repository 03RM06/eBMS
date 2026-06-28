package gov.brgy.ebms.clearance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ClearanceRequestDto(
    @NotNull Long residentId,
    @NotBlank @Size(max = 255) String purpose
) {}
