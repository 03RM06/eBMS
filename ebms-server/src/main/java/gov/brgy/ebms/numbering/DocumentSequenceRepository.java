package gov.brgy.ebms.numbering;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, DocumentSequenceId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ds FROM DocumentSequence ds WHERE ds.id.docType = :docType AND ds.id.seqYear = :year")
    Optional<DocumentSequence> findByDocTypeAndYearForUpdate(
        @Param("docType") String docType,
        @Param("year") int year
    );
}
