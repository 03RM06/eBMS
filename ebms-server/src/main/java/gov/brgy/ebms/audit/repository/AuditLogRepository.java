package gov.brgy.ebms.audit.repository;

import gov.brgy.ebms.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Insert-only audit log repository.
 *
 * Extends the narrow {@code Repository} marker interface rather than {@code JpaRepository}
 * so that inherited bulk-delete and bulk-update methods are never exposed at the application
 * code level. Only the operations explicitly declared here are available to callers:
 * {@code save()} for inserts and {@code find*} methods for reads.
 *
 * Database-level enforcement (REVOKE UPDATE, DELETE on audit_log from the app user) provides
 * the final guarantee. See docs/setup/create-db.sql and V2__db_user_grants.sql.
 */
@Repository
public interface AuditLogRepository
        extends org.springframework.data.repository.Repository<AuditLog, Long> {

    // ── Write ─────────────────────────────────────────────────────────────────

    <S extends AuditLog> S save(S entity);

    // ── Reads ─────────────────────────────────────────────────────────────────

    List<AuditLog> findAll(Sort sort);

    @Query("SELECT a FROM AuditLog a ORDER BY a.id DESC LIMIT 1")
    Optional<AuditLog> findLatest();

    @Query("SELECT a FROM AuditLog a WHERE a.entityType = :entityType AND a.entityId = :entityId ORDER BY a.createdAt ASC")
    List<AuditLog> findByEntityTypeAndEntityId(@Param("entityType") String entityType,
                                               @Param("entityId") Long entityId);

    List<AuditLog> findByActorUserId(Long actorUserId);

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
