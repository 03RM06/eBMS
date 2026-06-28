package gov.brgy.ebms.complaint.repository;

import gov.brgy.ebms.complaint.entity.ComplaintParty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplaintPartyRepository extends JpaRepository<ComplaintParty, Long> {

    List<ComplaintParty> findByComplaintId(Long complaintId);
}
