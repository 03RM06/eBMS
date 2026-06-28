package gov.brgy.ebms.desktop.api.dto;

import java.time.LocalDateTime;

public record HouseholdDto(
    Long id,
    String householdCode,
    Long headResidentId,
    String houseNo,
    String street,
    String purokSitio,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
