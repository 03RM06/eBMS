package gov.brgy.ebms.fee.repository;

import gov.brgy.ebms.fee.entity.Fee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeeRepository extends JpaRepository<Fee, Long> {

    @Query("SELECT f FROM Fee f WHERE f.orReference = :orReference")
    Optional<Fee> findByOrReference(@Param("orReference") String orReference);

    List<Fee> findByClearanceId(Long clearanceId);

    List<Fee> findByStatus(Fee.FeeStatus status);
}
