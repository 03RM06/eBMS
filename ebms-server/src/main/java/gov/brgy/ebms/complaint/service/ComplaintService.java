package gov.brgy.ebms.complaint.service;

import gov.brgy.ebms.audit.aspect.Auditable;
import gov.brgy.ebms.complaint.dto.ComplaintFilingRequest;
import gov.brgy.ebms.complaint.dto.ComplaintResponse;
import gov.brgy.ebms.complaint.entity.Complaint;
import gov.brgy.ebms.complaint.entity.Complaint.ComplaintStatus;
import gov.brgy.ebms.complaint.entity.ComplaintParty;
import gov.brgy.ebms.complaint.entity.ComplaintStatusHistory;
import gov.brgy.ebms.complaint.repository.ComplaintPartyRepository;
import gov.brgy.ebms.complaint.repository.ComplaintRepository;
import gov.brgy.ebms.complaint.repository.ComplaintStatusHistoryRepository;
import gov.brgy.ebms.numbering.DocumentNumberGenerator;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintPartyRepository partyRepository;
    private final ComplaintStatusHistoryRepository historyRepository;
    private final DocumentNumberGenerator documentNumberGenerator;

    public ComplaintService(
        ComplaintRepository complaintRepository,
        ComplaintPartyRepository partyRepository,
        ComplaintStatusHistoryRepository historyRepository,
        DocumentNumberGenerator documentNumberGenerator
    ) {
        this.complaintRepository = complaintRepository;
        this.partyRepository = partyRepository;
        this.historyRepository = historyRepository;
        this.documentNumberGenerator = documentNumberGenerator;
    }

    @Auditable(entityType = "COMPLAINT", action = "CREATE")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF')")
    @Transactional
    public ComplaintResponse file(ComplaintFilingRequest request, Long createdBy) {
        Complaint complaint = new Complaint();
        complaint.setCaseNumber(documentNumberGenerator.nextBlotterNumber());
        complaint.setTitle(request.title());
        complaint.setNarrative(request.narrative());
        complaint.setCreatedBy(createdBy);
        complaint = complaintRepository.save(complaint);

        for (ComplaintFilingRequest.PartyDto partyDto : request.parties()) {
            partyRepository.save(new ComplaintParty(
                complaint.getId(),
                partyDto.residentId(),
                partyDto.partyRole(),
                partyDto.displayName()
            ));
        }

        historyRepository.save(new ComplaintStatusHistory(
            complaint.getId(), null, ComplaintStatus.FILED.name(), "Complaint filed", createdBy
        ));

        return ComplaintResponse.from(complaint);
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF')")
    @Transactional(readOnly = true)
    public Page<ComplaintResponse> list(ComplaintStatus status, Pageable pageable) {
        if (status == null) {
            return complaintRepository.findAll(pageable).map(ComplaintResponse::from);
        }
        return complaintRepository.findByStatus(status, pageable).map(ComplaintResponse::from);
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF')")
    @Transactional(readOnly = true)
    public ComplaintResponse findById(Long id) {
        return ComplaintResponse.from(findEntityById(id));
    }

    @Auditable(entityType = "COMPLAINT", action = "STATUS_CHANGE")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY')")
    @Transactional
    public ComplaintResponse transition(Long id, ComplaintStatus newStatus, String note, Long changedBy) {
        Complaint complaint = findEntityById(id);
        validateTransition(complaint.getStatus(), newStatus);

        String fromStatus = complaint.getStatus().name();
        complaint.setStatus(newStatus);
        complaint.setUpdatedBy(changedBy);

        if (newStatus == ComplaintStatus.RESOLVED) {
            complaint.setResolvedAt(LocalDateTime.now());
            complaint.setResolutionNote(note);
        }

        complaint = complaintRepository.save(complaint);

        historyRepository.save(new ComplaintStatusHistory(
            complaint.getId(), fromStatus, newStatus.name(), note, changedBy
        ));

        return ComplaintResponse.from(complaint);
    }

    /**
     * Returns unresolved complaints (FILED or UNDER_MEDIATION) where the given
     * resident is a party. Used by ComplaintController's /unresolved endpoint.
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF')")
    @Transactional(readOnly = true)
    public java.util.List<ComplaintResponse> findUnresolved(Long residentId) {
        return complaintRepository.findUnresolvedByResidentParty(
            residentId,
            java.util.List.of(ComplaintStatus.FILED, ComplaintStatus.UNDER_MEDIATION)
        ).stream()
            .map(ComplaintResponse::from)
            .toList();
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN')")
    @Transactional
    public void delete(Long id, Long deletedBy) {
        Complaint complaint = findEntityById(id);
        complaint.softDelete(deletedBy);
        complaintRepository.save(complaint);
    }

    private void validateTransition(ComplaintStatus from, ComplaintStatus to) {
        boolean valid = switch (from) {
            case FILED -> to == ComplaintStatus.UNDER_MEDIATION || to == ComplaintStatus.ESCALATED;
            case UNDER_MEDIATION -> to == ComplaintStatus.RESOLVED || to == ComplaintStatus.ESCALATED;
            case RESOLVED, ESCALATED -> false;
        };
        if (!valid) {
            throw new IllegalStateException("Invalid status transition from " + from + " to " + to);
        }
    }

    private Complaint findEntityById(Long id) {
        return complaintRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Complaint not found: " + id));
    }
}
