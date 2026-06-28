package gov.brgy.ebms.audit;

import gov.brgy.ebms.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;

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
     * AC-050 / SEC-3: AuditLogRepository's declared methods must be either
     * {@code save} (insert-only) or read-oriented queries (find* / exist* / count*).
     * No delete, update, or bulk-mutation methods may appear.
     */
    @Test
    void auditLogRepository_customMethods_shouldBeInsertOrReadOnly() {
        List<String> declaredMethodNames = Arrays.stream(AuditLogRepository.class.getDeclaredMethods())
            .map(Method::getName)
            .toList();

        for (String methodName : declaredMethodNames) {
            assertThat(methodName)
                .as("AC-050: Declared method '%s' must be save (insert) or a read-only query", methodName)
                .satisfiesAnyOf(
                    m -> assertThat(m).isEqualTo("save"),
                    m -> assertThat(m).startsWith("find"),
                    m -> assertThat(m).startsWith("exist"),
                    m -> assertThat(m).startsWith("count")
                );
        }
    }

    /**
     * SEC-3: AuditLogRepository must extend the narrow {@code Repository} marker only —
     * not {@code JpaRepository} or {@code CrudRepository}, which expose bulk-delete
     * and bulk-update methods that must not be reachable on the audit log.
     */
    @Test
    void auditLogRepository_extendsNarrowRepositoryNotJpaRepository() {
        Class<?>[] interfaces = AuditLogRepository.class.getInterfaces();

        boolean extendsNarrowRepo = Arrays.stream(interfaces)
            .anyMatch(i -> i.equals(Repository.class));
        assertThat(extendsNarrowRepo)
            .as("SEC-3: AuditLogRepository must extend the narrow Repository marker interface")
            .isTrue();

        boolean extendsJpaRepo = Arrays.stream(interfaces)
            .anyMatch(i -> i.equals(JpaRepository.class));
        assertThat(extendsJpaRepo)
            .as("SEC-3: AuditLogRepository must NOT extend JpaRepository (exposes delete/update)")
            .isFalse();

        boolean extendsCrudRepo = Arrays.stream(interfaces)
            .anyMatch(i -> i.equals(CrudRepository.class));
        assertThat(extendsCrudRepo)
            .as("SEC-3: AuditLogRepository must NOT extend CrudRepository (exposes delete)")
            .isFalse();
    }
}
