package gov.brgy.ebms.resident.repository;

import gov.brgy.ebms.resident.entity.Resident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResidentRepository extends JpaRepository<Resident, Long> {

    Optional<Resident> findByResidentCode(String residentCode);

    List<Resident> findByDupKey(String dupKey);

    Page<Resident> findByLastNameContainingIgnoreCase(String lastName, Pageable pageable);

    @Query("SELECT r FROM Resident r WHERE " +
           "LOWER(r.firstName) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           "LOWER(r.lastName) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           "r.residentCode LIKE CONCAT('%',:q,'%')")
    Page<Resident> search(@Param("q") String query, Pageable pageable);

    @Query("SELECT r FROM Resident r WHERE r.householdId = :householdId")
    List<Resident> findByHouseholdId(@Param("householdId") Long householdId);

    /**
     * Bypasses the @SQLRestriction("deleted_at IS NULL") filter so restore() can find
     * soft-deleted residents. Native SQL sends the query as-is without the Hibernate filter.
     */
    @Query(value = "SELECT * FROM residents WHERE id = :id", nativeQuery = true)
    Optional<Resident> findByIdIncludeDeleted(@Param("id") Long id);
}
