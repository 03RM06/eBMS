package gov.brgy.ebms.desktop.api.dto;

import java.time.LocalDateTime;

public record AuditLogDto(
    Long id,
    String entityType,
    Long entityId,
    String action,
    String actorUsername,
    LocalDateTime createdAt,
    String beforeJson,
    String afterJson
) {}
