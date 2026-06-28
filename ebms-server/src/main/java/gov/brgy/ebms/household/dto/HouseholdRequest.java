package gov.brgy.ebms.household.dto;

import jakarta.validation.constraints.Size;

public record HouseholdRequest(
    Long headResidentId,
    @Size(max = 40) String houseNo,
    @Size(max = 120) String street,
    @Size(max = 80) String purokSitio
) {}
