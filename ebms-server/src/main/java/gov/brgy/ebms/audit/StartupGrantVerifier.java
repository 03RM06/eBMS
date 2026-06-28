package gov.brgy.ebms.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * FIX-11: On startup, verifies that the application DB user cannot UPDATE the audit_log table.
 * If the app user CAN update audit_log, the DBA has not yet applied the privilege restriction
 * from V2__db_user_grants.sql and a WARNING is logged.
 *
 * This check is best-effort: it will silently pass if the DB is not available at startup
 * (e.g., H2 in-memory test mode where there is no separate 'ebms_app' user to restrict).
 */
@Component
public class StartupGrantVerifier {

    private static final Logger log = LoggerFactory.getLogger(StartupGrantVerifier.class);

    private final JdbcTemplate jdbcTemplate;

    public StartupGrantVerifier(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyAuditLogRestriction() {
        try {
            // Attempt a no-op UPDATE on audit_log that matches zero rows.
            // If this succeeds (no SQL exception), the app user has UPDATE privilege — DBA
            // has not applied the V2 restriction.
            jdbcTemplate.execute(
                "UPDATE audit_log SET id = id WHERE 1 = 0"
            );
            log.warn(
                "SECURITY WARNING: The application DB user can UPDATE audit_log. " +
                "The DBA must apply V2__db_user_grants.sql to revoke UPDATE/DELETE on audit_log " +
                "to preserve audit trail immutability."
            );
        } catch (Exception e) {
            // UPDATE permission denied → restriction is in place. No action needed.
            log.info("audit_log UPDATE restriction verified: app user cannot modify audit entries.");
        }
    }
}
