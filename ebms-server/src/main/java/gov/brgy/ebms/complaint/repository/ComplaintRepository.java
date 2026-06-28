package gov.brgy.ebms.complaint.repository;

import gov.brgy.ebms.complaint.entity.Complaint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    Optional<Complaint> findByCaseNumber(String caseNumber);

    Page<Complaint> findByStatus(Complaint.ComplaintStatus status, Pageable pageable);

    /**
     * AC-033: Find unresolved complaints where the given resident is a party.
     * Used during clearance approval to warn (not block) if unresolved complaints exist.
     */
    @Query("SELECT c FROM Complaint c WHERE c.id IN " +
           "(SELECT cp.complaintId FROM ComplaintParty cp WHERE cp.residentId = :residentId) " +
           "AND c.status IN :statuses")
    List<Complaint> findUnresolvedByResidentParty(
        @Param("residentId") Long residentId,
        @Param("statuses") List<Complaint.ComplaintStatus> statuses);
}
