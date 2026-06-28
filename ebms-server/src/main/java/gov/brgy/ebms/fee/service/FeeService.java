package gov.brgy.ebms.fee.service;

import gov.brgy.ebms.audit.aspect.Auditable;
import gov.brgy.ebms.fee.dto.FeeRequest;
import gov.brgy.ebms.fee.dto.FeeResponse;
import gov.brgy.ebms.fee.entity.Fee;
import gov.brgy.ebms.fee.repository.FeeRepository;
import gov.brgy.ebms.numbering.DocumentNumberGenerator;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FeeService {

    private final FeeRepository feeRepository;
    private final DocumentNumberGenerator documentNumberGenerator;

    public FeeService(FeeRepository feeRepository, DocumentNumberGenerator documentNumberGenerator) {
        this.feeRepository = feeRepository;
        this.documentNumberGenerator = documentNumberGenerator;
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF')")
    @Transactional(readOnly = true)
    public List<FeeResponse> listUnpaid() {
        return feeRepository.findByStatus(Fee.FeeStatus.UNPAID).stream()
            .map(FeeResponse::from)
            .toList();
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF')")
    @Transactional(readOnly = true)
    public FeeResponse findById(Long id) {
        return FeeResponse.from(findEntityById(id));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF')")
    @Transactional
    public FeeResponse create(FeeRequest request, Long createdBy) {
        Fee fee = new Fee();
        fee.setClearanceId(request.clearanceId());
        fee.setFeeType(request.feeType() != null ? request.feeType() : "CLEARANCE");
        fee.setAmount(request.amount());
        fee.setCreatedBy(createdBy);
        return FeeResponse.from(feeRepository.save(fee));
    }

    @Auditable(entityType = "FEE", action = "PAYMENT")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF')")
    @Transactional
    public FeeResponse markPaid(Long id, Long collectedBy) {
        Fee fee = findEntityById(id);
        if (fee.getStatus() == Fee.FeeStatus.PAID) {
            throw new IllegalStateException("Fee is already paid.");
        }
        fee.setStatus(Fee.FeeStatus.PAID);
        fee.setPaidAt(LocalDateTime.now());
        fee.setCollectedBy(collectedBy);
        fee.setOrReference(documentNumberGenerator.nextOrReference());
        return FeeResponse.from(feeRepository.save(fee));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN')")
    @Transactional
    public FeeResponse waive(Long id, Long updatedBy) {
        Fee fee = findEntityById(id);
        fee.setStatus(Fee.FeeStatus.WAIVED);
        fee.setUpdatedBy(updatedBy);
        return FeeResponse.from(feeRepository.save(fee));
    }

    private Fee findEntityById(Long id) {
        return feeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Fee not found: " + id));
    }
}
