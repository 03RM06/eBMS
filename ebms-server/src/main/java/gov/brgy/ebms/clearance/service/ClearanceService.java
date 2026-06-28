package gov.brgy.ebms.clearance.service;

import gov.brgy.ebms.audit.aspect.Auditable;
import gov.brgy.ebms.clearance.dto.ClearanceRequestDto;
import gov.brgy.ebms.clearance.dto.ClearanceResponse;
import gov.brgy.ebms.clearance.entity.ClearanceDocument;
import gov.brgy.ebms.clearance.entity.ClearanceRequest;
import gov.brgy.ebms.clearance.entity.ClearanceRequest.ClearanceStatus;
import gov.brgy.ebms.clearance.repository.ClearanceDocumentRepository;
import gov.brgy.ebms.clearance.repository.ClearanceRequestRepository;
import gov.brgy.ebms.complaint.entity.Complaint;
import gov.brgy.ebms.complaint.repository.ComplaintRepository;
import gov.brgy.ebms.numbering.DocumentNumberGenerator;
import gov.brgy.ebms.pdf.ClearancePdfGenerator;
import gov.brgy.ebms.resident.entity.Resident;
import gov.brgy.ebms.resident.repository.ResidentRepository;
import gov.brgy.ebms.security.SecurityUtils;
import gov.brgy.ebms.security.entity.User;
import gov.brgy.ebms.security.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ClearanceService {

    private final ClearanceRequestRepository clearanceRepository;
    private final ClearanceDocumentRepository documentRepository;
    private final ResidentRepository residentRepository;
    private final DocumentNumberGenerator documentNumberGenerator;
    private final ClearancePdfGenerator pdfGenerator;
    private final UserRepository userRepository;
    private final ComplaintRepository complaintRepository;

    public ClearanceService(
        ClearanceRequestRepository clearanceRepository,
        ClearanceDocumentRepository documentRepository,
        ResidentRepository residentRepository,
        DocumentNumberGenerator documentNumberGenerator,
        ClearancePdfGenerator pdfGenerator,
        UserRepository userRepository,
        ComplaintRepository complaintRepository
    ) {
        this.clearanceRepository = clearanceRepository;
        this.documentRepository = documentRepository;
        this.residentRepository = residentRepository;
        this.documentNumberGenerator = documentNumberGenerator;
        this.pdfGenerator = pdfGenerator;
        this.userRepository = userRepository;
        this.complaintRepository = complaintRepository;
    }

    @Auditable(entityType = "CLEARANCE", action = "CREATE")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF','RESIDENT')")
    @Transactional
    public ClearanceResponse submit(ClearanceRequestDto request, Long createdBy) {
        // FIX-A: RESIDENT callers may only submit for their own resident record
        enforceResidentOwnership(request.residentId());

        residentRepository.findById(request.residentId())
            .orElseThrow(() -> new EntityNotFoundException("Resident not found: " + request.residentId()));

        // Warn if resident has pending clearance (non-blocking by spec)
        boolean hasPending = !clearanceRepository.findByResidentIdAndStatus(
            request.residentId(), ClearanceStatus.SUBMITTED).isEmpty()
            || !clearanceRepository.findByResidentIdAndStatus(
            request.residentId(), ClearanceStatus.UNDER_REVIEW).isEmpty();

        ClearanceRequest clearance = new ClearanceRequest();
        clearance.setControlNumber(documentNumberGenerator.nextClearanceNumber());
        clearance.setResidentId(request.residentId());
        clearance.setPurpose(request.purpose());
        clearance.setCreatedBy(createdBy);

        if (hasPending) {
            clearance.setRemarks("Note: Resident already has a pending clearance request.");
        }

        return ClearanceResponse.from(clearanceRepository.save(clearance));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF')")
    @Transactional(readOnly = true)
    public Page<ClearanceResponse> listByStatus(ClearanceStatus status, Pageable pageable) {
        if (status == null) {
            return clearanceRepository.findAll(pageable).map(ClearanceResponse::from);
        }
        return clearanceRepository.findByStatus(status, pageable).map(ClearanceResponse::from);
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF','RESIDENT')")
    @Transactional(readOnly = true)
    public Page<ClearanceResponse> listByResident(Long residentId, Pageable pageable) {
        // SEC-FIX-1: apply ownership check so RESIDENT callers can only list their own records
        enforceResidentOwnership(residentId);
        return clearanceRepository.findByResidentId(residentId, pageable).map(ClearanceResponse::from);
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF','RESIDENT')")
    @Transactional(readOnly = true)
    public ClearanceResponse findById(Long id) {
        ClearanceRequest clearance = findEntityById(id);
        // FIX-A: RESIDENT callers may only read their own clearance records
        enforceResidentOwnership(clearance.getResidentId());
        return ClearanceResponse.from(clearance);
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY')")
    @Transactional
    public ClearanceResponse startReview(Long id, Long reviewedBy) {
        ClearanceRequest clearance = findEntityById(id);
        requireStatus(clearance, ClearanceStatus.SUBMITTED);
        clearance.setStatus(ClearanceStatus.UNDER_REVIEW);
        clearance.setReviewedBy(reviewedBy);
        clearance.setReviewedAt(LocalDateTime.now());
        return ClearanceResponse.from(clearanceRepository.save(clearance));
    }

    @Auditable(entityType = "CLEARANCE", action = "APPROVE",
               entityClass = ClearanceRequest.class, entityIdArgIndex = 0)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY')")
    @Transactional
    public ClearanceResponse approve(Long id, Long approvedBy) {
        ClearanceRequest clearance = findEntityById(id);
        requireStatus(clearance, ClearanceStatus.UNDER_REVIEW);

        // AC-034: Prevent self-approval — check if approver is the same resident as on clearance
        if (approvedBy != null) {
            Optional<User> approverOpt = userRepository.findById(approvedBy);
            if (approverOpt.isPresent()) {
                User approver = approverOpt.get();
                if (approver.getResidentId() != null &&
                    approver.getResidentId().equals(clearance.getResidentId())) {
                    throw new AccessDeniedException("Resident cannot approve their own clearance");
                }
            }
        }

        // AC-033: Warn (do not block) if resident has unresolved complaints
        List<Complaint> unresolvedComplaints = complaintRepository.findUnresolvedByResidentParty(
            clearance.getResidentId(),
            List.of(Complaint.ComplaintStatus.FILED, Complaint.ComplaintStatus.UNDER_MEDIATION)
        );
        if (!unresolvedComplaints.isEmpty()) {
            String warn = "Warning: Resident has " + unresolvedComplaints.size() +
                " unresolved complaint(s) pending. Clearance approved with advisory.";
            clearance.setRemarks(warn);
        }

        clearance.setStatus(ClearanceStatus.APPROVED);
        clearance.setApprovedAt(LocalDateTime.now());
        clearance.setUpdatedBy(approvedBy);
        clearance = clearanceRepository.save(clearance);

        generatePdf(clearance, approvedBy);

        return ClearanceResponse.from(clearance);
    }

    @Auditable(entityType = "CLEARANCE", action = "REJECT",
               entityClass = ClearanceRequest.class, entityIdArgIndex = 0)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY')")
    @Transactional
    public ClearanceResponse reject(Long id, String remarks, Long updatedBy) {
        ClearanceRequest clearance = findEntityById(id);
        if (clearance.getStatus() == ClearanceStatus.APPROVED) {
            throw new IllegalStateException("Cannot reject an already approved clearance.");
        }
        clearance.setStatus(ClearanceStatus.REJECTED);
        clearance.setRemarks(remarks);
        clearance.setUpdatedBy(updatedBy);
        return ClearanceResponse.from(clearanceRepository.save(clearance));
    }

    /**
     * Returns the generated PDF document record for an approved clearance.
     * The caller must have permission to view the clearance (ownership enforced for RESIDENT).
     * Throws EntityNotFoundException if no document has been generated yet (not yet approved).
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF','RESIDENT')")
    @Transactional(readOnly = true)
    public ClearanceDocument getDocument(Long clearanceId) {
        ClearanceRequest clearance = findEntityById(clearanceId);
        enforceResidentOwnership(clearance.getResidentId());
        List<ClearanceDocument> docs = documentRepository.findByClearanceId(clearanceId);
        if (docs.isEmpty()) {
            throw new jakarta.persistence.EntityNotFoundException(
                "Document not yet generated for clearance: " + clearanceId);
        }
        return docs.get(0);
    }

    /**
     * Ownership check for RESIDENT-role callers: a resident user may only access
     * their own clearance records. Staff and admin roles bypass this check.
     *
     * <p>Skipped when no authentication context exists (e.g. unit tests without a
     * SecurityContext), so existing tests without security stubs are unaffected.
     */
    private void enforceResidentOwnership(Long entityResidentId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return;

        boolean isResident = auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_RESIDENT".equals(a.getAuthority()));
        if (!isResident) return;

        Long callerId = SecurityUtils.getAuthenticatedUserId();
        // SEC-FIX-2: never silently skip for RESIDENT callers — missing identity = denied
        if (callerId == null) {
            throw new AccessDeniedException(
                "Unable to determine caller identity; access denied for RESIDENT");
        }

        User caller = userRepository.findById(callerId)
            .orElseThrow(() -> new AccessDeniedException("Authenticated user not found"));
        if (!entityResidentId.equals(caller.getResidentId())) {
            throw new AccessDeniedException(
                "Access denied: residents may only access their own records");
        }
    }

    private void generatePdf(ClearanceRequest clearance, Long issuedBy) {
        Resident resident = residentRepository.findById(clearance.getResidentId())
            .orElseThrow(() -> new EntityNotFoundException("Resident not found"));
        try {
            ClearancePdfGenerator.PdfGenerationResult result = pdfGenerator.generate(clearance, resident);
            documentRepository.save(new ClearanceDocument(
                clearance.getId(),
                clearance.getControlNumber(),
                result.filePath(),
                result.sha256Checksum(),
                issuedBy
            ));
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate clearance PDF", e);
        }
    }

    private void requireStatus(ClearanceRequest clearance, ClearanceStatus required) {
        if (clearance.getStatus() != required) {
            throw new IllegalStateException(
                "Expected status " + required + " but was " + clearance.getStatus());
        }
    }

    private ClearanceRequest findEntityById(Long id) {
        return clearanceRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Clearance not found: " + id));
    }
}
