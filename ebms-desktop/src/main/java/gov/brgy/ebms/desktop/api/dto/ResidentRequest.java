package gov.brgy.ebms.desktop.api.dto;

import java.time.LocalDate;

public record ResidentRequest(
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
    boolean confirmDuplicate
) {}
