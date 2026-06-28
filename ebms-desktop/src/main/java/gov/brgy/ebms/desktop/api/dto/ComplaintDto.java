package gov.brgy.ebms.desktop.api.dto;

import java.time.LocalDateTime;

public record ComplaintDto(
    Long id,
    String caseNumber,
    String title,
    String narrative,
    String status,
    LocalDateTime filedAt,
    LocalDateTime resolvedAt,
    String resolutionNote,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
