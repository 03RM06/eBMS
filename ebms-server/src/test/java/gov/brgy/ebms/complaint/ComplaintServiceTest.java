package gov.brgy.ebms.complaint;

import gov.brgy.ebms.complaint.dto.ComplaintFilingRequest;
import gov.brgy.ebms.complaint.dto.ComplaintResponse;
import gov.brgy.ebms.complaint.entity.Complaint;
import gov.brgy.ebms.complaint.entity.Complaint.ComplaintStatus;
import gov.brgy.ebms.complaint.entity.ComplaintParty;
import gov.brgy.ebms.complaint.entity.ComplaintStatusHistory;
import gov.brgy.ebms.complaint.repository.ComplaintPartyRepository;
import gov.brgy.ebms.complaint.repository.ComplaintRepository;
import gov.brgy.ebms.complaint.repository.ComplaintStatusHistoryRepository;
import gov.brgy.ebms.complaint.service.ComplaintService;
import gov.brgy.ebms.numbering.DocumentNumberGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for AC-040, AC-041, AC-042.
 */
@ExtendWith(MockitoExtension.class)
class ComplaintServiceTest {

    @Mock private ComplaintRepository complaintRepository;
    @Mock private ComplaintPartyRepository partyRepository;
    @Mock private ComplaintStatusHistoryRepository historyRepository;
    @Mock private DocumentNumberGenerator documentNumberGenerator;

    @InjectMocks private ComplaintService complaintService;

    // ─── AC-040 ────────────────────────────────────────────────────────────────

    /**
     * AC-040: Filing a complaint must assign a case number in format BRGY-BLT-YYYY-NNNNNN.
     */
    @Test
    void file_shouldAssignCaseNumberWithCorrectFormat() {
        int year = java.time.LocalDate.now().getYear();
        String expectedCaseNumber = "BRGY-BLT-" + year + "-000001";

        when(documentNumberGenerator.nextBlotterNumber()).thenReturn(expectedCaseNumber);

        Complaint savedComplaint = new Complaint();
        savedComplaint.setId(1L);
        savedComplaint.setCaseNumber(expectedCaseNumber);
        savedComplaint.setTitle("Noise disturbance");
        savedComplaint.setNarrative("Loud music at night");
        when(complaintRepository.save(any())).thenReturn(savedComplaint);
        when(partyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ComplaintFilingRequest request = new ComplaintFilingRequest(
            "Noise disturbance",
            "Loud music at night",
            List.of(new ComplaintFilingRequest.PartyDto(1L, "Juan Dela Cruz", ComplaintParty.PartyRole.COMPLAINANT))
        );

        ComplaintResponse response = complaintService.file(request, 99L);

        assertThat(response.caseNumber())
            .as("AC-040: case number must follow BRGY-BLT-YYYY-NNNNNN format")
            .matches("BRGY-BLT-\\d{4}-\\d{6}");
    }

    /**
     * AC-040: Filing sets initial status to FILED.
     */
    @Test
    void file_shouldSetInitialStatusToFiled() {
        when(documentNumberGenerator.nextBlotterNumber()).thenReturn("BRGY-BLT-2026-000001");

        Complaint savedComplaint = new Complaint();
        savedComplaint.setId(1L);
        savedComplaint.setCaseNumber("BRGY-BLT-2026-000001");
        savedComplaint.setTitle("Test");
        savedComplaint.setNarrative("Narrative");
        // default status is FILED per Complaint entity
        when(complaintRepository.save(any())).thenReturn(savedComplaint);
        when(partyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ComplaintFilingRequest request = new ComplaintFilingRequest(
            "Test", "Narrative",
            List.of(new ComplaintFilingRequest.PartyDto(1L, "Juan", ComplaintParty.PartyRole.COMPLAINANT))
        );

        ComplaintResponse response = complaintService.file(request, 99L);

        assertThat(response.status())
            .as("AC-040: Initial status must be FILED")
            .isEqualTo("FILED");
    }

    // ─── AC-041 ────────────────────────────────────────────────────────────────

    /**
     * AC-041: Each status transition must be persisted in complaint_status_history
     * with actor (changedBy) and timestamp (changedAt).
     */
    @Test
    void transition_shouldPersistStatusHistoryWithActorAndTimestamp() {
        Complaint complaint = buildComplaint(1L, ComplaintStatus.FILED);
        when(complaintRepository.findById(1L)).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any())).thenReturn(complaint);

        ArgumentCaptor<ComplaintStatusHistory> historyCaptor =
            ArgumentCaptor.forClass(ComplaintStatusHistory.class);
        when(historyRepository.save(historyCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        complaintService.transition(1L, ComplaintStatus.UNDER_MEDIATION, "Mediation started", 42L);

        ComplaintStatusHistory saved = historyCaptor.getValue();
        assertThat(saved.getFromStatus())
            .as("AC-041: history must record from-status")
            .isEqualTo("FILED");
        assertThat(saved.getToStatus())
            .as("AC-041: history must record to-status")
            .isEqualTo("UNDER_MEDIATION");
        assertThat(saved.getChangedBy())
            .as("AC-041: history must record actor (changedBy)")
            .isEqualTo(42L);
        assertThat(saved.getChangedAt())
            .as("AC-041: history must have a timestamp")
            .isNotNull();
    }

    /**
     * AC-041: Filing a new complaint also writes an initial history entry with FILED status.
     */
    @Test
    void file_shouldWriteInitialHistoryEntry() {
        when(documentNumberGenerator.nextBlotterNumber()).thenReturn("BRGY-BLT-2026-000001");

        Complaint savedComplaint = new Complaint();
        savedComplaint.setId(1L);
        savedComplaint.setCaseNumber("BRGY-BLT-2026-000001");
        savedComplaint.setTitle("Test");
        savedComplaint.setNarrative("Narrative");
        when(complaintRepository.save(any())).thenReturn(savedComplaint);
        when(partyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<ComplaintStatusHistory> historyCaptor =
            ArgumentCaptor.forClass(ComplaintStatusHistory.class);
        when(historyRepository.save(historyCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        complaintService.file(new ComplaintFilingRequest(
            "Test", "Narrative",
            List.of(new ComplaintFilingRequest.PartyDto(1L, "Juan", ComplaintParty.PartyRole.COMPLAINANT))
        ), 7L);

        ComplaintStatusHistory initialHistory = historyCaptor.getValue();
        assertThat(initialHistory.getToStatus())
            .as("AC-041: Initial history entry must have status FILED")
            .isEqualTo("FILED");
        assertThat(initialHistory.getChangedBy())
            .as("AC-041: Initial history entry must record the filing actor")
            .isEqualTo(7L);
    }

    // ─── AC-042 ────────────────────────────────────────────────────────────────

    /**
     * AC-042: RESOLVED → FILED is an invalid transition and must be rejected (409).
     * The service throws IllegalStateException which the GlobalExceptionHandler maps to 409.
     */
    @Test
    void transition_resolvedToFiled_shouldThrowIllegalStateException() {
        Complaint complaint = buildComplaint(1L, ComplaintStatus.RESOLVED);
        when(complaintRepository.findById(1L)).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() ->
            complaintService.transition(1L, ComplaintStatus.FILED, "Re-open", 99L)
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("Invalid status transition");
    }

    /**
     * AC-042: ESCALATED → FILED must also be rejected.
     */
    @Test
    void transition_escalatedToFiled_shouldThrowIllegalStateException() {
        Complaint complaint = buildComplaint(1L, ComplaintStatus.ESCALATED);
        when(complaintRepository.findById(1L)).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() ->
            complaintService.transition(1L, ComplaintStatus.FILED, "Back to start", 99L)
        ).isInstanceOf(IllegalStateException.class);
    }

    /**
     * AC-042: RESOLVED → UNDER_MEDIATION must also be rejected.
     */
    @Test
    void transition_resolvedToUnderMediation_shouldThrowIllegalStateException() {
        Complaint complaint = buildComplaint(1L, ComplaintStatus.RESOLVED);
        when(complaintRepository.findById(1L)).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() ->
            complaintService.transition(1L, ComplaintStatus.UNDER_MEDIATION, "Reopen", 99L)
        ).isInstanceOf(IllegalStateException.class);
    }

    /**
     * AC-042: FILED → UNDER_MEDIATION is a valid transition and must succeed.
     */
    @Test
    void transition_filedToUnderMediation_shouldSucceed() {
        Complaint complaint = buildComplaint(1L, ComplaintStatus.FILED);
        when(complaintRepository.findById(1L)).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any())).thenReturn(complaint);
        when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ComplaintResponse response = complaintService.transition(1L, ComplaintStatus.UNDER_MEDIATION, null, 99L);

        assertThat(response).isNotNull();
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private Complaint buildComplaint(Long id, ComplaintStatus status) {
        Complaint c = new Complaint();
        c.setId(id);
        c.setCaseNumber("BRGY-BLT-2026-000001");
        c.setTitle("Test");
        c.setNarrative("Narrative");
        c.setStatus(status);
        return c;
    }
}
