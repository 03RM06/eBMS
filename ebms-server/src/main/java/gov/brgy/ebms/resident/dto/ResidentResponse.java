package gov.brgy.ebms.resident.dto;

import gov.brgy.ebms.resident.entity.Resident;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ResidentResponse(
    Long id,
    String residentCode,
    String firstName,
    String middleName,
    String lastName,
    String suffix,
    LocalDate birthdate,
    String sex,
    String civilStatus,
    String contactNumber,
    String email,
    String houseNo,
    String street,
    String purokSitio,
    Long householdId,
    String occupation,
    boolean isVoter,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static ResidentResponse from(Resident r) {
        return new ResidentResponse(
            r.getId(), r.getResidentCode(),
            r.getFirstName(), r.getMiddleName(), r.getLastName(), r.getSuffix(),
            r.getBirthdate(),
            r.getSex() != null ? r.getSex().name() : null,
            r.getCivilStatus() != null ? r.getCivilStatus().name() : null,
            r.getContactNumber(), r.getEmail(),
            r.getHouseNo(), r.getStreet(), r.getPurokSitio(),
            r.getHouseholdId(), r.getOccupation(), r.isVoter(),
            r.getCreatedAt(), r.getUpdatedAt()
        );
    }
}
