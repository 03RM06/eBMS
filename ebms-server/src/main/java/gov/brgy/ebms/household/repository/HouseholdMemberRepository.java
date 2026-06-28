package gov.brgy.ebms.household.repository;

import gov.brgy.ebms.household.entity.HouseholdMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, Long> {

    List<HouseholdMember> findByHouseholdId(Long householdId);

    Optional<HouseholdMember> findByHouseholdIdAndResidentId(Long householdId, Long residentId);

    @Query("SELECT hm FROM HouseholdMember hm WHERE hm.residentId = :residentId")
    List<HouseholdMember> findActiveByResidentId(@Param("residentId") Long residentId);
}
