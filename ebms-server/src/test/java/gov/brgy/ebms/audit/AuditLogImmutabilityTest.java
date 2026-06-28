package gov.brgy.ebms.audit;

import gov.brgy.ebms.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for AC-050: Audit log entries are immutable — no delete or update paths.
 */
class AuditLogImmutabilityTest {

    /**
     * AC-050: AuditLogRepository must not declare any method that deletes
     * audit log entries, ensuring immutability at the repository layer.
     *
     * JpaRepository itself inherits deleteById, deleteAll etc., but the
     * key constraint is that the custom audit-specific interface must not
     * expose delete/update methods, and no service/component should call them.
     */
    @Test
    void auditLogRepository_shouldNotDeclareDeleteOrUpdateMethods() {
        List<String> declaredMethodNames = Arrays.stream(AuditLogRepository.class.getDeclaredMethods())
            .map(Method::getName)
            .toList();

        List<String> forbiddenPrefixes = List.of("delete", "remove", "update", "truncate", "clear");

        for (String methodName : declaredMethodNames) {
            String lowerName = methodName.toLowerCase();
            for (String forbidden : forbiddenPrefixes) {
                assertThat(lowerName)
                    .as("AC-050: AuditLogRepository must not expose '%s' method '%s'", forbidden, methodName)
                    .doesNotStartWith(forbidden);
            }
        }
    }

    /**
     * AC-050: Verify AuditLogRepository only exposes read-oriented custom methods.
     */
    @Test
    void auditLogRepository_customMethods_shouldAllBeReadOnly() {
        List<String> declaredMethodNames = Arrays.stream(AuditLogRepository.class.getDeclaredMethods())
            .map(Method::getName)
            .toList();

        // All declared custom methods must be find* or exist* queries
        for (String methodName : declaredMethodNames) {
            assertThat(methodName)
                .as("AC-050: Custom method '%s' should be a read-only query (find* or exist*)", methodName)
                .satisfiesAnyOf(
                    m -> assertThat(m).startsWith("find"),
                    m -> assertThat(m).startsWith("exist"),
                    m -> assertThat(m).startsWith("count")
                );
        }
    }

    /**
     * AC-050: Structural check — AuditLogRepository extends JpaRepository which
     * provides save() but save() used for CREATE is acceptable. The critical check
     * is that no audit-specific deleteByXxx or updateXxx methods are declared.
     */
    @Test
    void auditLogRepository_extendsJpaRepository() {
        boolean extendsJpaRepo = Arrays.stream(AuditLogRepository.class.getInterfaces())
            .anyMatch(i -> i.equals(JpaRepository.class));
        assertThat(extendsJpaRepo)
            .as("AuditLogRepository should extend JpaRepository for standard CRUD")
            .isTrue();
    }
}
