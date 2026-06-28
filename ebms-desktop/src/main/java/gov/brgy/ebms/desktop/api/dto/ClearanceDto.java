package gov.brgy.ebms.desktop.api.dto;

import java.time.LocalDateTime;

public record ClearanceDto(
    Long id,
    String controlNumber,
    Long residentId,
    String purpose,
    String status,
    String remarks,
    Long feeId,
    LocalDateTime approvedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
