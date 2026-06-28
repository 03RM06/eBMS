package gov.brgy.ebms.clearance.repository;

import gov.brgy.ebms.clearance.entity.ClearanceRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClearanceRequestRepository extends JpaRepository<ClearanceRequest, Long> {

    Optional<ClearanceRequest> findByControlNumber(String controlNumber);

    Page<ClearanceRequest> findByResidentId(Long residentId, Pageable pageable);

    Page<ClearanceRequest> findByStatus(ClearanceRequest.ClearanceStatus status, Pageable pageable);

    List<ClearanceRequest> findByResidentIdAndStatus(Long residentId, ClearanceRequest.ClearanceStatus status);
}
