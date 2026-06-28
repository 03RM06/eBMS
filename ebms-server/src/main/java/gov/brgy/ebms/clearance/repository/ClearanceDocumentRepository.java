package gov.brgy.ebms.clearance.repository;

import gov.brgy.ebms.clearance.entity.ClearanceDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClearanceDocumentRepository extends JpaRepository<ClearanceDocument, Long> {

    List<ClearanceDocument> findByClearanceId(Long clearanceId);
}
