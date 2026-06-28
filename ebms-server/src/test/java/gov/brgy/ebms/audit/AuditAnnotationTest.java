package gov.brgy.ebms.audit;

import gov.brgy.ebms.audit.aspect.Auditable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for AC-011 and AC-050:
 * - AC-011: Audit log captures before/after on edits (requires @Auditable on service methods)
 * - AC-050: Create/update/approve actions produce audit entries with actor, action, entity,
 *   timestamp, before/after JSON.
 *
 * These tests verify structural requirements: that @Auditable is actually applied to the
 * service methods that the spec requires to be audited.
 *
 * DEFECT-05: @Auditable is defined but applied to ZERO methods in any service class.
 * The AuditAspect pointcut (@Around("@annotation(auditable)")) will NEVER fire.
 * All tests in this class WILL FAIL exposing the defect.
 */
class AuditAnnotationTest {

    private static final String RESIDENT_SERVICE =
        "gov.brgy.ebms.resident.service.ResidentService";
    private static final String CLEARANCE_SERVICE =
        "gov.brgy.ebms.clearance.service.ClearanceService";

    /**
     * AC-011: ResidentService.create() must be annotated with @Auditable so that
     * audit entries are created when a new resident is registered.
     */
    @Test
    void residentService_createMethod_shouldBeAnnotatedWithAuditable() throws Exception {
        Class<?> clazz = Class.forName(RESIDENT_SERVICE);
        Method createMethod = findMethodByName(clazz, "create");

        assertThat(createMethod)
            .as("ResidentService must have a create() method")
            .isNotNull();
        assertThat(createMethod.isAnnotationPresent(Auditable.class))
            .as("AC-011/AC-050: ResidentService.create() must be annotated @Auditable — DEFECT-05")
            .isTrue();
    }

    /**
     * AC-011: ResidentService.update() must be annotated with @Auditable so that
     * before/after JSON is captured on resident edits.
     */
    @Test
    void residentService_updateMethod_shouldBeAnnotatedWithAuditable() throws Exception {
        Class<?> clazz = Class.forName(RESIDENT_SERVICE);
        Method updateMethod = findMethodByName(clazz, "update");

        assertThat(updateMethod)
            .as("ResidentService must have an update() method")
            .isNotNull();
        assertThat(updateMethod.isAnnotationPresent(Auditable.class))
            .as("AC-011/AC-050: ResidentService.update() must be annotated @Auditable — DEFECT-05")
            .isTrue();
    }

    /**
     * AC-050: ClearanceService.approve() must be annotated with @Auditable so that
     * clearance approvals produce an immutable audit entry.
     */
    @Test
    void clearanceService_approveMethod_shouldBeAnnotatedWithAuditable() throws Exception {
        Class<?> clazz = Class.forName(CLEARANCE_SERVICE);
        Method approveMethod = findMethodByName(clazz, "approve");

        assertThat(approveMethod)
            .as("ClearanceService must have an approve() method")
            .isNotNull();
        assertThat(approveMethod.isAnnotationPresent(Auditable.class))
            .as("AC-050: ClearanceService.approve() must be annotated @Auditable — DEFECT-05")
            .isTrue();
    }

    /**
     * AC-050: At least one service class must apply @Auditable to verify the audit
     * infrastructure is actually wired up, not just defined.
     */
    @Test
    void atLeastOneServiceMethod_shouldBeAnnotatedWithAuditable() throws Exception {
        List<Class<?>> serviceClasses = List.of(
            Class.forName(RESIDENT_SERVICE),
            Class.forName(CLEARANCE_SERVICE),
            Class.forName("gov.brgy.ebms.complaint.service.ComplaintService")
        );

        long auditableMethodCount = serviceClasses.stream()
            .flatMap(c -> Arrays.stream(c.getDeclaredMethods()))
            .filter(m -> m.isAnnotationPresent(Auditable.class))
            .count();

        assertThat(auditableMethodCount)
            .as("AC-050: At least one service method must carry @Auditable to produce audit entries — "
                + "DEFECT-05: @Auditable is never applied, AuditAspect never fires")
            .isGreaterThan(0);
    }

    private Method findMethodByName(Class<?> clazz, String name) {
        return Arrays.stream(clazz.getDeclaredMethods())
            .filter(m -> m.getName().equals(name))
            .findFirst()
            .orElse(null);
    }
}
