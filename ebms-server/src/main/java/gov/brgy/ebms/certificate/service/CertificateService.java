package gov.brgy.ebms.certificate.service;

import gov.brgy.ebms.audit.aspect.Auditable;
import gov.brgy.ebms.certificate.dto.CertificateRequest;
import gov.brgy.ebms.certificate.dto.CertificateResponse;
import gov.brgy.ebms.certificate.entity.Certificate;
import gov.brgy.ebms.certificate.entity.CertificateStatus;
import gov.brgy.ebms.certificate.entity.CertificateType;
import gov.brgy.ebms.certificate.repository.CertificateRepository;
import gov.brgy.ebms.numbering.DocumentNumberGenerator;
import gov.brgy.ebms.pdf.CertificatePdfGenerator;
import gov.brgy.ebms.pdf.ClearancePdfGenerator;
import gov.brgy.ebms.resident.entity.Resident;
import gov.brgy.ebms.resident.repository.ResidentRepository;
import gov.brgy.ebms.security.SecurityUtils;
import gov.brgy.ebms.security.entity.User;
import gov.brgy.ebms.security.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Service
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);

    private final CertificateRepository certificateRepository;
    private final ResidentRepository residentRepository;
    private final DocumentNumberGenerator documentNumberGenerator;
    private final CertificatePdfGenerator pdfGenerator;
    private final UserRepository userRepository;

    public CertificateService(
        CertificateRepository certificateRepository,
        ResidentRepository residentRepository,
        DocumentNumberGenerator documentNumberGenerator,
        CertificatePdfGenerator pdfGenerator,
        UserRepository userRepository
    ) {
        this.certificateRepository = certificateRepository;
        this.residentRepository = residentRepository;
        this.documentNumberGenerator = documentNumberGenerator;
        this.pdfGenerator = pdfGenerator;
        this.userRepository = userRepository;
    }

    @Auditable(entityType = "CERTIFICATE", action = "CREATE")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF','RESIDENT')")
    @Transactional
    public CertificateResponse submit(CertificateRequest request, Long createdBy) {
        enforceResidentOwnership(request.residentId());
        residentRepository.findById(request.residentId())
            .orElseThrow(() -> new EntityNotFoundException("Resident not found: " + request.residentId()));

        Certificate cert = new Certificate();
        cert.setControlNumber(documentNumberGenerator.nextCertificateNumber());
        cert.setResidentId(request.residentId());
        cert.setCertificateType(request.certificateType());
        cert.setPurpose(request.purpose());
        cert.setCreatedBy(createdBy);

        return CertificateResponse.from(certificateRepository.save(cert));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF')")
    @Transactional(readOnly = true)
    public Page<CertificateResponse> listAll(CertificateType type, CertificateStatus status, Pageable pageable) {
        if (type != null && status != null)
            return certificateRepository.findByStatusAndCertificateType(status, type, pageable).map(CertificateResponse::from);
        if (type != null)
            return certificateRepository.findByCertificateType(type, pageable).map(CertificateResponse::from);
        if (status != null)
            return certificateRepository.findByStatus(status, pageable).map(CertificateResponse::from);
        return certificateRepository.findAll(pageable).map(CertificateResponse::from);
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF','RESIDENT')")
    @Transactional(readOnly = true)
    public CertificateResponse findById(Long id) {
        Certificate cert = findEntityById(id);
        enforceResidentOwnership(cert.getResidentId());
        return CertificateResponse.from(cert);
    }

    @Auditable(entityType = "CERTIFICATE", action = "APPROVE",
               entityClass = Certificate.class, entityIdArgIndex = 0)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY')")
    @Transactional
    public CertificateResponse approve(Long id, Long approvedBy) {
        Certificate cert = findEntityById(id);
        if (cert.getStatus() != CertificateStatus.REQUESTED) {
            throw new IllegalStateException(
                "Cannot approve a certificate with status " + cert.getStatus());
        }
        Resident resident = residentRepository.findById(cert.getResidentId())
            .orElseThrow(() -> new EntityNotFoundException("Resident not found"));

        try {
            ClearancePdfGenerator.PdfGenerationResult result = pdfGenerator.generate(cert, resident);
            cert.setFilePath(result.filePath());
            cert.setSha256Checksum(result.sha256Checksum());
        } catch (IOException e) {
            log.error("Failed to generate PDF for certificate {}", id, e);
            throw new RuntimeException("Failed to generate certificate PDF", e);
        }

        cert.setStatus(CertificateStatus.APPROVED);
        cert.setApprovedBy(approvedBy);
        cert.setApprovedAt(LocalDateTime.now());
        cert.setUpdatedBy(approvedBy);
        return CertificateResponse.from(certificateRepository.save(cert));
    }

    @Auditable(entityType = "CERTIFICATE", action = "REJECT",
               entityClass = Certificate.class, entityIdArgIndex = 0)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY')")
    @Transactional
    public CertificateResponse reject(Long id, String remarks, Long updatedBy) {
        Certificate cert = findEntityById(id);
        if (cert.getStatus() == CertificateStatus.APPROVED) {
            throw new IllegalStateException("Cannot reject an already approved certificate.");
        }
        cert.setStatus(CertificateStatus.REJECTED);
        cert.setRemarks(remarks);
        cert.setUpdatedBy(updatedBy);
        return CertificateResponse.from(certificateRepository.save(cert));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF','RESIDENT')")
    @Transactional(readOnly = true)
    public String getFilePath(Long id) {
        Certificate cert = findEntityById(id);
        enforceResidentOwnership(cert.getResidentId());
        if (cert.getStatus() != CertificateStatus.APPROVED || cert.getFilePath() == null) {
            throw new IllegalStateException("Certificate not yet approved: " + id);
        }
        return cert.getFilePath();
    }

    private void enforceResidentOwnership(Long entityResidentId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return;

        boolean isResident = auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_RESIDENT".equals(a.getAuthority()));
        if (!isResident) return;

        Long callerId = SecurityUtils.getAuthenticatedUserId();
        if (callerId == null) {
            throw new AccessDeniedException("Unable to determine caller identity; access denied for RESIDENT");
        }
        User caller = userRepository.findById(callerId)
            .orElseThrow(() -> new AccessDeniedException("Authenticated user not found"));
        if (!entityResidentId.equals(caller.getResidentId())) {
            throw new AccessDeniedException("Access denied: residents may only access their own records");
        }
    }

    private Certificate findEntityById(Long id) {
        return certificateRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Certificate not found: " + id));
    }
}
