package gov.brgy.ebms.audit;

import gov.brgy.ebms.audit.aspect.AuditAspect;
import gov.brgy.ebms.audit.aspect.Auditable;
import gov.brgy.ebms.audit.service.AuditService;
import gov.brgy.ebms.clearance.service.ClearanceService;
import gov.brgy.ebms.complaint.entity.Complaint;
import gov.brgy.ebms.complaint.service.ComplaintService;
import gov.brgy.ebms.resident.dto.ResidentRequest;
import gov.brgy.ebms.resident.dto.ResidentResponse;
import gov.brgy.ebms.resident.service.ResidentService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exposes AC-011 / AC-050 defect:
 * {@link AuditAspect#audit} always passes {@code null} for the before-state argument,
 * so UPDATE and DELETE operations produce audit entries with {@code beforeJson = null}.
 *
 * <p>Spec requirements:
 * <ul>
 *   <li>AC-011: Audit log captures before/after on edits</li>
 *   <li>AC-050: Audit entries written with actor/action/entity/before-after</li>
 * </ul>
 *
 * <p>The two tests in this class WILL FAIL against the current implementation.
 * They document the gap and must be fixed by capturing the entity state
 * before {@code joinPoint.proceed()} is called for UPDATE/DELETE operations.
 */
@ExtendWith(MockitoExtension.class)
class AuditAspectBeforeStateTest {

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuditAspect auditAspect;

    /**
     * AC-011 / AC-050 DEFECT: For an UPDATE action the before-state must be non-null.
     * AuditAspect.audit() always passes {@code null} regardless of the action type.
     *
     * <p>This test WILL FAIL until AuditAspect is fixed to load and snapshot the entity
     * state before {@code joinPoint.proceed()} for UPDATE operations.
     */
    @Test
    void audit_forUpdateAction_beforeStateMustBeNonNull_AC011_Defect() throws Throwable {
        // Get the @Auditable annotation from the actual ResidentService.update() method
        Method updateMethod = ResidentService.class.getDeclaredMethod(
            "update", Long.class, ResidentRequest.class, Long.class);
        Auditable auditableAnnotation = updateMethod.getAnnotation(Auditable.class);

        assertThat(auditableAnnotation)
            .as("Precondition: ResidentService.update() must carry @Auditable")
            .isNotNull();
        assertThat(auditableAnnotation.action())
            .as("Precondition: action must be UPDATE")
            .isEqualTo("UPDATE");

        // Mock a ProceedingJoinPoint that returns a resident response ("after" state)
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        ResidentResponse afterState = new ResidentResponse(
            1L, "RES-2026-000001", "AfterFirst", null, "AfterLast", null,
            LocalDate.of(1990, 1, 1), "MALE", "SINGLE",
            null, null, null, null, null, null, null, false,
            LocalDateTime.now(), LocalDateTime.now()
        );
        when(joinPoint.proceed()).thenReturn(afterState);

        // Execute the aspect
        auditAspect.audit(joinPoint, auditableAnnotation);

        // Capture the 6th argument ('before') passed to AuditService.log()
        ArgumentCaptor<Object> beforeCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).log(
            eq("RESIDENT"),          // entityType
            any(),                   // entityId
            eq("UPDATE"),            // action
            any(),                   // actorUserId
            any(),                   // actorUsername
            beforeCaptor.capture(),  // BEFORE STATE — must not be null for UPDATE
            any(),                   // after state
            any()                    // ipAddress
        );

        // AC-011 / AC-050 DEFECT: AuditAspect always passes null for before-state.
        // The spec requires the entity state before modification to be captured on edits.
        // This assertion WILL FAIL — defect exposed.
        assertThat(beforeCaptor.getValue())
            .as("AC-011 DEFECT: AuditAspect.audit() hardcodes null for before-state. "
                + "UPDATE operations must snapshot the entity before joinPoint.proceed().")
            .isNotNull();
    }

    /**
     * AC-011 / AC-050 DEFECT: For a DELETE action the before-state (entity being deleted)
     * must also be non-null. Currently both before and after are null for DELETE.
     */
    @Test
    void audit_forDeleteAction_beforeStateMustBeNonNull_AC011_Defect() throws Throwable {
        // Get the @Auditable annotation from ResidentService.delete()
        Method deleteMethod = ResidentService.class.getDeclaredMethod(
            "delete", Long.class, Long.class);
        Auditable auditableAnnotation = deleteMethod.getAnnotation(Auditable.class);

        assertThat(auditableAnnotation)
            .as("Precondition: ResidentService.delete() must carry @Auditable")
            .isNotNull();
        assertThat(auditableAnnotation.action())
            .as("Precondition: action must be DELETE")
            .isEqualTo("DELETE");

        // delete() returns void — joinPoint.proceed() returns null
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(null);

        // Execute the aspect
        auditAspect.audit(joinPoint, auditableAnnotation);

        // Capture before state
        ArgumentCaptor<Object> beforeCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).log(
            eq("RESIDENT"),
            any(),
            eq("DELETE"),
            any(),
            any(),
            beforeCaptor.capture(), // before — must be the deleted entity, not null
            any(),
            any()
        );

        assertThat(beforeCaptor.getValue())
            .as("AC-011: DELETE audit entry before-state must be non-null.")
            .isNotNull();
    }

    /**
     * ARCH-1 fix: AuditAspect must capture before-state for REJECT actions
     * now that entityClass is declared on ClearanceService.reject().
     */
    @Test
    void audit_forRejectAction_beforeStateMustBeNonNull() throws Throwable {
        Method rejectMethod = ClearanceService.class.getDeclaredMethod(
            "reject", Long.class, String.class, Long.class);
        Auditable auditableAnnotation = rejectMethod.getAnnotation(Auditable.class);

        assertThat(auditableAnnotation).as("ClearanceService.reject() must carry @Auditable").isNotNull();
        assertThat(auditableAnnotation.action()).isEqualTo("REJECT");

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(null);

        auditAspect.audit(joinPoint, auditableAnnotation);

        ArgumentCaptor<Object> beforeCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).log(
            eq("CLEARANCE"), any(), eq("REJECT"), any(), any(),
            beforeCaptor.capture(), any(), any()
        );

        assertThat(beforeCaptor.getValue())
            .as("ARCH-1: REJECT audit entry before-state must be non-null after AuditAspect fix")
            .isNotNull();
    }

    /**
     * ARCH-1 fix: AuditAspect must capture before-state for STATUS_CHANGE actions
     * now that entityClass is declared on ComplaintService.transition().
     */
    @Test
    void audit_forStatusChangeAction_beforeStateMustBeNonNull() throws Throwable {
        Method transitionMethod = ComplaintService.class.getDeclaredMethod(
            "transition", Long.class, Complaint.ComplaintStatus.class, String.class, Long.class);
        Auditable auditableAnnotation = transitionMethod.getAnnotation(Auditable.class);

        assertThat(auditableAnnotation).as("ComplaintService.transition() must carry @Auditable").isNotNull();
        assertThat(auditableAnnotation.action()).isEqualTo("STATUS_CHANGE");

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(null);

        auditAspect.audit(joinPoint, auditableAnnotation);

        ArgumentCaptor<Object> beforeCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).log(
            eq("COMPLAINT"), any(), eq("STATUS_CHANGE"), any(), any(),
            beforeCaptor.capture(), any(), any()
        );

        assertThat(beforeCaptor.getValue())
            .as("ARCH-1: STATUS_CHANGE audit entry before-state must be non-null after AuditAspect fix")
            .isNotNull();
    }
}
