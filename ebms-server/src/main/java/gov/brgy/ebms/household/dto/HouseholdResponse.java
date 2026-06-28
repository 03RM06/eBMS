package gov.brgy.ebms.household.dto;

import gov.brgy.ebms.household.entity.Household;

import java.time.LocalDateTime;

public record HouseholdResponse(
    Long id,
    String householdCode,
    Long headResidentId,
    String houseNo,
    String street,
    String purokSitio,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static HouseholdResponse from(Household h) {
        return new HouseholdResponse(
            h.getId(), h.getHouseholdCode(), h.getHeadResidentId(),
            h.getHouseNo(), h.getStreet(), h.getPurokSitio(),
            h.getCreatedAt(), h.getUpdatedAt()
        );
    }
}
