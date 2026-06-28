package gov.brgy.ebms.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.brgy.ebms.audit.entity.AuditLog;
import gov.brgy.ebms.audit.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(
        String entityType,
        Long entityId,
        String action,
        Long actorUserId,
        String actorUsername,
        Object before,
        Object after,
        String ipAddress
    ) {
        String beforeJson = toJson(before);
        String afterJson = toJson(after);

        String prevHash = auditLogRepository.findLatest()
            .map(AuditLog::getRowHash)
            .orElse(null);

        String rowHash = computeHash(entityType, entityId, action, actorUsername, beforeJson, afterJson, prevHash);

        AuditLog entry = new AuditLog();
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setActorUserId(actorUserId);
        entry.setActorUsername(actorUsername);
        entry.setBeforeJson(beforeJson);
        entry.setAfterJson(afterJson);
        entry.setIpAddress(ipAddress);
        entry.setPrevHash(prevHash);
        entry.setRowHash(rowHash);

        auditLogRepository.save(entry);
    }

    /**
     * Filtered paged audit log search. All parameters are optional (pass null to skip).
     * Requires BARANGAY_CAPTAIN or SUPER_ADMIN role.
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN')")
    @Transactional(readOnly = true)
    public Page<AuditLog> findFiltered(
        String entityType,
        Long entityId,
        Long actorId,
        LocalDateTime from,
        LocalDateTime to,
        Pageable pageable
    ) {
        return auditLogRepository.findFiltered(entityType, entityId, actorId, from, to, pageable);
    }

    /**
     * Verifies the integrity of the hash chain across all audit log rows.
     * Loads all rows in ID-ascending order and recomputes each rowHash.
     * Requires SUPER_ADMIN role.
     *
     * @return verification result containing validity flag, total row count,
     *         and (if invalid) the ID of the first row where the hash does not match.
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public HashChainVerificationResult verifyHashChain() {
        List<AuditLog> rows = auditLogRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        long total = rows.size();

        String prevHash = null;
        for (AuditLog row : rows) {
            String expected = computeHash(
                row.getEntityType(), row.getEntityId(), row.getAction(),
                row.getActorUsername(), row.getBeforeJson(), row.getAfterJson(),
                prevHash
            );
            if (!expected.equals(row.getRowHash())) {
                return new HashChainVerificationResult(false, total, row.getId());
            }
            prevHash = row.getRowHash();
        }
        return new HashChainVerificationResult(true, total, null);
    }

    public record HashChainVerificationResult(boolean valid, long totalRows, Long brokenAtId) {}

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"serialization failed\"}";
        }
    }

    private String computeHash(String entityType, Long entityId, String action,
                               String actorUsername, String beforeJson, String afterJson, String prevHash) {
        String data = entityType + "|" + entityId + "|" + action + "|"
            + actorUsername + "|" + beforeJson + "|" + afterJson + "|" + prevHash;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
