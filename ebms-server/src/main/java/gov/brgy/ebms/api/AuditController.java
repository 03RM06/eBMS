package gov.brgy.ebms.api;

import gov.brgy.ebms.audit.dto.AuditLogResponse;
import gov.brgy.ebms.audit.service.AuditService;
import gov.brgy.ebms.audit.service.AuditService.HashChainVerificationResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * REST controller for audit log access and hash-chain integrity verification.
 *
 * <p>Authorization is enforced by @PreAuthorize on the AuditService methods.
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Returns a paged, filtered view of the audit log.
     * All query parameters are optional.
     * Requires BARANGAY_CAPTAIN or SUPER_ADMIN role.
     *
     * @param entityType filter by entity type (e.g. "RESIDENT", "CLEARANCE")
     * @param entityId   filter by specific entity ID
     * @param actorId    filter by actor user ID
     * @param from       filter entries on or after this datetime (ISO-8601)
     * @param to         filter entries on or before this datetime (ISO-8601)
     */
    @GetMapping
    public ResponseEntity<Page<AuditLogResponse>> search(
        @RequestParam(required = false) String entityType,
        @RequestParam(required = false) Long entityId,
        @RequestParam(required = false) Long actorId,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
        Pageable pageable
    ) {
        Page<AuditLogResponse> page = auditService
            .findFiltered(entityType, entityId, actorId, from, to, pageable)
            .map(AuditLogResponse::from);
        return ResponseEntity.ok(page);
    }

    /**
     * Verifies the SHA-256 hash chain across all audit log rows.
     * Requires SUPER_ADMIN role.
     *
     * @return {@code {valid, totalRows, brokenAtId}} — brokenAtId is null when valid
     */
    @GetMapping("/verify")
    public ResponseEntity<HashChainVerificationResult> verify() {
        return ResponseEntity.ok(auditService.verifyHashChain());
    }
}
