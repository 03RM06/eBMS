package gov.brgy.ebms.audit.aspect;

import gov.brgy.ebms.audit.service.AuditService;
import gov.brgy.ebms.security.SecurityUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditService auditService;

    /**
     * Injected by Spring at runtime for entity lookups; remains {@code null} in pure
     * unit tests (Mockito {@code @InjectMocks}) where the fallback sentinel is used instead.
     */
    @PersistenceContext
    private EntityManager entityManager;

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Long actorUserId = SecurityUtils.getAuthenticatedUserId();
        String username = resolveUsername();
        String ipAddress = resolveIpAddress();

        // ── AC-011 / AC-050: capture before-state for any action that declares entityClass ─
        // Applies to UPDATE, DELETE, REJECT, STATUS_CHANGE, and any future mutating actions.
        Object beforeState = null;
        Long entityId = null;

        if (auditable.entityClass() != Void.class) {
            try {
                Object[] args = joinPoint.getArgs();
                if (args != null && args.length > auditable.entityIdArgIndex()) {
                    Object idArg = args[auditable.entityIdArgIndex()];
                    if (idArg instanceof Long id) {
                        entityId = id;
                        if (entityManager != null) {
                            beforeState = entityManager.find(auditable.entityClass(), entityId);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Could not capture before-state for audit [{}.{}]",
                    auditable.entityType(), auditable.action(), e);
            }

            // Guarantee a non-null before-state even when entity lookup is unavailable
            // (EntityManager null in unit tests, or entity already gone).
            // AC-011 requires before/after to be non-null on edits.
            // sentinel=true distinguishes this fallback from a real entity snapshot in
            // audit log queries, so operators can identify capture failures in production.
            if (beforeState == null) {
                beforeState = Map.of(
                    "entityType", auditable.entityType(),
                    "entityId",   entityId != null ? entityId.toString() : "unknown",
                    "reason",     entityId != null ? "entity_not_found_at_capture_time"
                                                   : "entity_id_not_extractable",
                    "sentinel",   "true"
                );
            }
        }

        // ── Execute the business operation ────────────────────────────────────
        Object result = joinPoint.proceed();

        // ── Resolve entity ID from result when not already extracted from args ─
        if (entityId == null) {
            entityId = extractEntityId(result);
        }

        auditService.log(
            auditable.entityType(),
            entityId,
            auditable.action(),
            actorUserId,
            username,
            beforeState,
            result,
            ipAddress
        );

        return result;
    }

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
    }

    private String resolveIpAddress() {
        try {
            ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                return request.getRemoteAddr();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Long extractEntityId(Object result) {
        if (result == null) {
            return null;
        }
        try {
            var method = result.getClass().getMethod("id");
            Object id = method.invoke(result);
            if (id instanceof Long l) {
                return l;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
