package gov.brgy.ebms.audit.dto;

import gov.brgy.ebms.audit.entity.AuditLog;

import java.time.LocalDateTime;

public record AuditLogResponse(
    Long id,
    String entityType,
    Long entityId,
    String action,
    String actorUsername,
    LocalDateTime createdAt,
    String beforeJson,
    String afterJson
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
            log.getId(),
            log.getEntityType(),
            log.getEntityId(),
            log.getAction(),
            log.getActorUsername(),
            log.getCreatedAt(),
            log.getBeforeJson(),
            log.getAfterJson()
        );
    }
}
