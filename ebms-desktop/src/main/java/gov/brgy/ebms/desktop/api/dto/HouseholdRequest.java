package gov.brgy.ebms.desktop.api.dto;

public record HouseholdRequest(
    Long headResidentId,
    String houseNo,
    String street,
    String purokSitio
) {}
