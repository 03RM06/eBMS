package gov.brgy.ebms.audit.repository;

import gov.brgy.ebms.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SEC-FIX-3: Insert-only audit log repository.
 * Extends JpaRepository for standard CRUD (required by AC-050 structural test), but only
 * find/count query methods are explicitly declared — no delete or update methods are added.
 * DB-level enforcement (REVOKE UPDATE, DELETE on audit_log) provides the final guarantee.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a WHERE a.entityType = :entityType AND a.entityId = :entityId ORDER BY a.createdAt ASC")
    List<AuditLog> findByEntityTypeAndEntityId(@Param("entityType") String entityType,
                                               @Param("entityId") Long entityId);

    List<AuditLog> findByActorUserId(Long actorUserId);

    @Query("SELECT a FROM AuditLog a ORDER BY a.id DESC LIMIT 1")
    Optional<AuditLog> findLatest();

    /**
     * Filtered paged search for AuditController.
     * All optional parameters: pass null to skip that filter.
     */
    @Query(value = "SELECT a FROM AuditLog a WHERE " +
           "(:entityType IS NULL OR a.entityType = :entityType) AND " +
           "(:entityId IS NULL OR a.entityId = :entityId) AND " +
           "(:actorId IS NULL OR a.actorUserId = :actorId) AND " +
           "(:from IS NULL OR a.createdAt >= :from) AND " +
           "(:to IS NULL OR a.createdAt <= :to) " +
           "ORDER BY a.createdAt DESC",
           countQuery = "SELECT COUNT(a) FROM AuditLog a WHERE " +
           "(:entityType IS NULL OR a.entityType = :entityType) AND " +
           "(:entityId IS NULL OR a.entityId = :entityId) AND " +
           "(:actorId IS NULL OR a.actorUserId = :actorId) AND " +
           "(:from IS NULL OR a.createdAt >= :from) AND " +
           "(:to IS NULL OR a.createdAt <= :to)")
    Page<AuditLog> findFiltered(@Param("entityType") String entityType,
                                @Param("entityId") Long entityId,
                                @Param("actorId") Long actorId,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to,
                                Pageable pageable);
}
