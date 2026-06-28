package gov.brgy.ebms.desktop.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ResidentDto(
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
) {}
