package gov.brgy.ebms.resident.dto;

import gov.brgy.ebms.resident.entity.Resident;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record ResidentRequest(
    @NotBlank @Size(max = 80) String firstName,
    @Size(max = 80) String middleName,
    @NotBlank @Size(max = 80) String lastName,
    @Size(max = 16) String suffix,
    @NotNull @Past LocalDate birthdate,
    @NotNull Resident.Sex sex,
    Resident.CivilStatus civilStatus,
    @Size(max = 20) String contactNumber,
    @Email @Size(max = 128) String email,
    @Size(max = 40) String houseNo,
    @Size(max = 120) String street,
    @Size(max = 80) String purokSitio,
    Long householdId,
    @Size(max = 120) String occupation,
    boolean isVoter,
    boolean confirmDuplicate
) {
    // Compact constructor with default for confirmDuplicate
    public ResidentRequest {
        // confirmDuplicate defaults to false when not provided (Java record default for boolean)
    }
}
