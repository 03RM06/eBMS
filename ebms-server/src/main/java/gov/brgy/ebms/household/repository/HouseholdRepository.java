package gov.brgy.ebms.household.repository;

import gov.brgy.ebms.household.entity.Household;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HouseholdRepository extends JpaRepository<Household, Long> {

    Optional<Household> findByHouseholdCode(String householdCode);

    boolean existsByHeadResidentId(Long headResidentId);
}
