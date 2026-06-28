package gov.brgy.ebms.clearance;

import gov.brgy.ebms.clearance.dto.ClearanceRequestDto;
import gov.brgy.ebms.clearance.dto.ClearanceResponse;
import gov.brgy.ebms.clearance.entity.ClearanceRequest;
import gov.brgy.ebms.clearance.entity.ClearanceRequest.ClearanceStatus;
import gov.brgy.ebms.clearance.repository.ClearanceDocumentRepository;
import gov.brgy.ebms.clearance.repository.ClearanceRequestRepository;
import gov.brgy.ebms.clearance.service.ClearanceService;
import gov.brgy.ebms.complaint.entity.Complaint;
import gov.brgy.ebms.complaint.repository.ComplaintRepository;
import gov.brgy.ebms.numbering.DocumentNumberGenerator;
import gov.brgy.ebms.pdf.ClearancePdfGenerator;
import gov.brgy.ebms.resident.entity.Resident;
import gov.brgy.ebms.resident.repository.ResidentRepository;
import gov.brgy.ebms.security.entity.User;
import gov.brgy.ebms.security.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for AC-030, AC-031, AC-033, AC-034.
 */
@ExtendWith(MockitoExtension.class)
class ClearanceServiceTest {

    @Mock private ClearanceRequestRepository clearanceRepository;
    @Mock private ClearanceDocumentRepository documentRepository;
    @Mock private ResidentRepository residentRepository;
    @Mock private DocumentNumberGenerator documentNumberGenerator;
    @Mock private ClearancePdfGenerator pdfGenerator;
    @Mock private UserRepository userRepository;
    @Mock private ComplaintRepository complaintRepository;

    @InjectMocks private ClearanceService clearanceService;

    // ─── AC-030 ────────────────────────────────────────────────────────────────

    /**
     * AC-030: Submitting a clearance request should result in SUBMITTED status
     * and return a tracking reference (control number).
     */
    @Test
    void submit_shouldReturnSubmittedStatusWithControlNumber() {
        ClearanceRequestDto dto = new ClearanceRequestDto(1L, "Employment");

        Resident resident = buildResident(1L);
        when(residentRepository.findById(1L)).thenReturn(Optional.of(resident));
        when(clearanceRepository.findByResidentIdAndStatus(1L, ClearanceStatus.SUBMITTED))
            .thenReturn(Collections.emptyList());
        when(clearanceRepository.findByResidentIdAndStatus(1L, ClearanceStatus.UNDER_REVIEW))
            .thenReturn(Collections.emptyList());
        when(documentNumberGenerator.nextClearanceNumber()).thenReturn("BRGY-CLR-2026-000001");

        ClearanceRequest savedRequest = buildClearanceRequest(1L, 1L, ClearanceStatus.SUBMITTED);
        savedRequest.setControlNumber("BRGY-CLR-2026-000001");
        when(clearanceRepository.save(any())).thenReturn(savedRequest);

        ClearanceResponse response = clearanceService.submit(dto, 99L);

        assertThat(response).isNotNull();
        assertThat(response.status())
            .as("AC-030: Status must be SUBMITTED after submit()")
            .isEqualTo("SUBMITTED");
        assertThat(response.controlNumber())
            .as("AC-030: Must return a tracking reference (control number)")
            .isNotBlank()
            .startsWith("BRGY-CLR-");
    }

    /**
     * AC-030: Submitting for a non-existent resident must throw EntityNotFoundException.
     */
    @Test
    void submit_nonExistentResident_shouldThrowNotFound() {
        when(residentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            clearanceService.submit(new ClearanceRequestDto(999L, "Employment"), 1L)
        ).isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    // ─── AC-031 ────────────────────────────────────────────────────────────────

    /**
     * AC-031: Approving an UNDER_REVIEW clearance should set status APPROVED
     * and trigger PDF generation.
     * approvedBy=99L has no linked residentId (Optional.empty default) so no self-approval block.
     * No unresolved complaints (complaintRepository returns empty list by default).
     */
    @Test
    void approve_underReview_shouldSetApprovedAndGeneratePdf() throws IOException {
        ClearanceRequest clearance = buildClearanceRequest(1L, 10L, ClearanceStatus.UNDER_REVIEW);
        clearance.setControlNumber("BRGY-CLR-2026-000001");

        when(clearanceRepository.findById(1L)).thenReturn(Optional.of(clearance));
        when(clearanceRepository.save(any())).thenReturn(clearance);

        Resident resident = buildResident(10L);
        when(residentRepository.findById(10L)).thenReturn(Optional.of(resident));

        ClearancePdfGenerator.PdfGenerationResult pdfResult =
            new ClearancePdfGenerator.PdfGenerationResult("./test.pdf", "abc123");
        when(pdfGenerator.generate(any(), any())).thenReturn(pdfResult);
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // userRepository.findById(99L) returns Optional.empty() by default → self-approval skipped
        // complaintRepository.findUnresolvedByResidentParty returns [] by default → no warning

        ClearanceResponse response = clearanceService.approve(1L, 99L);

        assertThat(response.status())
            .as("AC-031: Status must be APPROVED after approve()")
            .isEqualTo("APPROVED");
        verify(pdfGenerator, times(1)).generate(any(), any());
    }

    /**
     * AC-031: Approving a clearance not in UNDER_REVIEW status must be rejected.
     */
    @Test
    void approve_notUnderReview_shouldThrow() {
        ClearanceRequest clearance = buildClearanceRequest(1L, 10L, ClearanceStatus.SUBMITTED);
        when(clearanceRepository.findById(1L)).thenReturn(Optional.of(clearance));

        // requireStatus() throws before user/complaint lookup — no other stubs needed
        assertThatThrownBy(() -> clearanceService.approve(1L, 99L))
            .isInstanceOf(IllegalStateException.class);
    }

    // ─── AC-033 ────────────────────────────────────────────────────────────────

    /**
     * AC-033: If the resident has an unresolved complaint, clearance approval must
     * WARN (not block). The warning must appear in the response.remarks field.
     */
    @Test
    void approve_whenResidentHasUnresolvedComplaint_shouldWarnInResponse() throws IOException {
        ClearanceRequest clearance = buildClearanceRequest(1L, 10L, ClearanceStatus.UNDER_REVIEW);
        clearance.setControlNumber("BRGY-CLR-2026-000001");

        when(clearanceRepository.findById(1L)).thenReturn(Optional.of(clearance));
        when(clearanceRepository.save(any())).thenReturn(clearance);

        Resident resident = buildResident(10L);
        when(residentRepository.findById(10L)).thenReturn(Optional.of(resident));

        ClearancePdfGenerator.PdfGenerationResult pdfResult =
            new ClearancePdfGenerator.PdfGenerationResult("./test.pdf", "abc123");
        when(pdfGenerator.generate(any(), any())).thenReturn(pdfResult);
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // AC-033: resident 10 has an unresolved complaint
        Complaint unresolved = new Complaint();
        unresolved.setId(1L);
        unresolved.setCaseNumber("BRGY-BLT-2026-000001");
        unresolved.setTitle("Test complaint");
        unresolved.setNarrative("Narrative");
        unresolved.setStatus(Complaint.ComplaintStatus.FILED);
        when(complaintRepository.findUnresolvedByResidentParty(eq(10L), anyList()))
            .thenReturn(List.of(unresolved));

        // userRepository.findById(99L) returns Optional.empty() by default → no self-approval block

        ClearanceResponse response = clearanceService.approve(1L, 99L);

        // Spec AC-033: approval must WARN (not block) if resident has unresolved complaint.
        assertThat(response.remarks())
            .as("AC-033: Response must contain a warning about unresolved complaint")
            .isNotNull()
            .containsIgnoringCase("complaint");
        assertThat(response.status())
            .as("AC-033: Approval must still proceed (warn-only, not blocked)")
            .isEqualTo("APPROVED");
    }

    // ─── AC-034 ────────────────────────────────────────────────────────────────

    /**
     * AC-034: A resident must not be able to approve their own clearance.
     * If the approving user's residentId matches the clearance's residentId, throw 403.
     */
    @Test
    void approve_whenApproverIsTheSameResidentAsOnClearance_shouldThrow403() {
        // Clearance is for resident 10
        ClearanceRequest clearance = buildClearanceRequest(1L, 10L, ClearanceStatus.UNDER_REVIEW);
        clearance.setControlNumber("BRGY-CLR-2026-000001");

        when(clearanceRepository.findById(1L)).thenReturn(Optional.of(clearance));

        // approvedBy user ID 5 whose residentId is 10 (same as the clearance residentId)
        User approverUser = new User();
        approverUser.setId(5L);
        approverUser.setUsername("staff-who-is-also-resident-10");
        approverUser.setFullName("Staff Resident");
        approverUser.setResidentId(10L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(approverUser));

        // Exception must be thrown BEFORE save/PDF — no other stubs needed
        assertThatThrownBy(() -> clearanceService.approve(1L, 5L))
            .as("AC-034: Self-approval must be forbidden (AccessDeniedException / 403)")
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private ClearanceRequest buildClearanceRequest(Long id, Long residentId, ClearanceStatus status) {
        ClearanceRequest req = new ClearanceRequest();
        req.setId(id);
        req.setResidentId(residentId);
        req.setPurpose("Employment");
        req.setStatus(status);
        return req;
    }

    private Resident buildResident(Long id) {
        Resident r = new Resident();
        r.setId(id);
        r.setFirstName("Juan");
        r.setLastName("Dela Cruz");
        r.setBirthdate(LocalDate.of(1990, 1, 1));
        r.setSex(Resident.Sex.MALE);
        r.setCivilStatus(Resident.CivilStatus.SINGLE);
        r.setResidentCode("RES-2026-000001");
        r.setDupKey("juan|dela cruz|1990-01-01");
        return r;
    }
}
