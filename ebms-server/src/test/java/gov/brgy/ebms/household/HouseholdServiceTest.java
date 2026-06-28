package gov.brgy.ebms.household;

import gov.brgy.ebms.household.dto.HouseholdRequest;
import gov.brgy.ebms.household.entity.Household;
import gov.brgy.ebms.household.entity.HouseholdMember;
import gov.brgy.ebms.household.repository.HouseholdMemberRepository;
import gov.brgy.ebms.household.repository.HouseholdRepository;
import gov.brgy.ebms.household.service.HouseholdService;
import gov.brgy.ebms.numbering.DocumentNumberGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for AC-020 and AC-021.
 */
@ExtendWith(MockitoExtension.class)
class HouseholdServiceTest {

    @Mock private HouseholdRepository householdRepository;
    @Mock private HouseholdMemberRepository memberRepository;
    @Mock private DocumentNumberGenerator documentNumberGenerator;

    @InjectMocks private HouseholdService householdService;

    /**
     * AC-020: Creating a household with a head resident should save a member
     * record with role "HEAD".
     */
    @Test
    void create_withHead_shouldSaveOneMemberRecordWithHeadRole() {
        HouseholdRequest request = new HouseholdRequest(10L, "12", "Rizal St", "Purok 1");

        when(householdRepository.existsByHeadResidentId(10L)).thenReturn(false);
        when(documentNumberGenerator.nextHouseholdCode()).thenReturn("HH-2026-000001");

        Household savedHousehold = new Household();
        savedHousehold.setId(1L);
        savedHousehold.setHouseholdCode("HH-2026-000001");
        savedHousehold.setHeadResidentId(10L);
        when(householdRepository.save(any())).thenReturn(savedHousehold);
        when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        householdService.create(request, 99L);

        ArgumentCaptor<HouseholdMember> memberCaptor = ArgumentCaptor.forClass(HouseholdMember.class);
        verify(memberRepository, times(1)).save(memberCaptor.capture());

        HouseholdMember savedMember = memberCaptor.getValue();
        assertThat(savedMember.getRelationship())
            .as("AC-020: The member record for the head must have role 'HEAD'")
            .isEqualTo("HEAD");
        assertThat(savedMember.getResidentId())
            .as("AC-020: The HEAD member must be the head resident")
            .isEqualTo(10L);
    }

    /**
     * AC-020: Creating a household without a head should not save any member records.
     */
    @Test
    void create_withoutHead_shouldNotSaveAnyMemberRecord() {
        HouseholdRequest request = new HouseholdRequest(null, "12", "Rizal St", "Purok 1");

        when(documentNumberGenerator.nextHouseholdCode()).thenReturn("HH-2026-000002");

        Household savedHousehold = new Household();
        savedHousehold.setId(2L);
        when(householdRepository.save(any())).thenReturn(savedHousehold);

        householdService.create(request, 99L);

        verify(memberRepository, never()).save(any());
    }

    /**
     * AC-021: Assigning a resident as head when they already head another household
     * must be rejected.
     */
    @Test
    void create_whenResidentAlreadyHeadsAnotherHousehold_shouldThrow() {
        HouseholdRequest request = new HouseholdRequest(10L, "12", "Rizal St", "Purok 1");

        when(householdRepository.existsByHeadResidentId(10L)).thenReturn(true);

        assertThatThrownBy(() -> householdService.create(request, 99L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("head");
    }

    /**
     * AC-021: Updating a household to set a new head who already heads another
     * household must be rejected.
     */
    @Test
    void update_whenNewHeadAlreadyHeadsAnotherHousehold_shouldThrow() {
        Household existing = new Household();
        existing.setId(1L);
        existing.setHeadResidentId(5L); // current head is resident 5

        when(householdRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(householdRepository.existsByHeadResidentId(20L)).thenReturn(true); // resident 20 already heads another

        HouseholdRequest request = new HouseholdRequest(20L, "12", "Rizal St", "Purok 1");

        assertThatThrownBy(() -> householdService.update(1L, request, 99L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * AC-021: Updating a household to keep the same head should not be rejected
     * (idempotent).
     */
    @Test
    void update_keepingSameHead_shouldSucceed() {
        Household existing = new Household();
        existing.setId(1L);
        existing.setHeadResidentId(5L);

        when(householdRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(householdRepository.save(any())).thenReturn(existing);

        HouseholdRequest request = new HouseholdRequest(5L, "12", "Rizal St", "Purok 1");

        householdService.update(1L, request, 99L);

        verify(householdRepository, never()).existsByHeadResidentId(5L);
    }

    /**
     * ARCH-4: setHouseholdHead must reject a resident who already heads another household.
     */
    @Test
    void setHouseholdHead_whenResidentAlreadyHeadsAnotherHousehold_shouldThrow() {
        Household household = new Household();
        household.setId(1L);
        household.setHeadResidentId(5L); // current head is resident 5

        when(householdRepository.findById(1L)).thenReturn(Optional.of(household));
        when(householdRepository.existsByHeadResidentId(8L)).thenReturn(true); // resident 8 heads another

        assertThatThrownBy(() -> householdService.setHouseholdHead(1L, 8L, 99L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already the head");
    }

    /**
     * Security Low F4: setHouseholdHead with null residentId must throw immediately
     * rather than propagate a DB constraint violation.
     */
    @Test
    void setHouseholdHead_withNullResidentId_shouldThrowIllegalArgument() {
        assertThatThrownBy(() -> householdService.setHouseholdHead(1L, null, 99L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("residentId");
    }

    /**
     * ARCH-3 fix: setHouseholdHead should demote the previous head member row
     * to MEMBER and promote the new head in household_members.
     */
    @Test
    void setHouseholdHead_shouldDemotePreviousHeadAndPromoteNewHead() {
        Household household = new Household();
        household.setId(1L);
        household.setHeadResidentId(5L); // current head

        HouseholdMember previousHeadMember = new HouseholdMember(1L, 5L, "HEAD", 1L);
        HouseholdMember newHeadMember = new HouseholdMember(1L, 7L, "MEMBER", 1L);

        when(householdRepository.findById(1L)).thenReturn(Optional.of(household));
        when(memberRepository.findByHouseholdIdAndResidentId(1L, 5L)).thenReturn(Optional.of(previousHeadMember));
        when(memberRepository.findByHouseholdIdAndResidentId(1L, 7L)).thenReturn(Optional.of(newHeadMember));
        when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(householdRepository.save(any())).thenReturn(household);

        householdService.setHouseholdHead(1L, 7L, 99L);

        assertThat(previousHeadMember.getRelationship()).isEqualTo("MEMBER");
        assertThat(newHeadMember.getRelationship()).isEqualTo("HEAD");
        assertThat(household.getHeadResidentId()).isEqualTo(7L);
    }

    /**
     * ARCH-3 fix: when the new head has no existing member row, setHouseholdHead
     * must insert a new household_members record with role HEAD.
     */
    @Test
    void setHouseholdHead_whenNewHeadNotYetMember_shouldInsertMemberRecord() {
        Household household = new Household();
        household.setId(1L);
        household.setHeadResidentId(null); // no previous head

        when(householdRepository.findById(1L)).thenReturn(Optional.of(household));
        when(memberRepository.findByHouseholdIdAndResidentId(1L, 8L)).thenReturn(Optional.empty());
        when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(householdRepository.save(any())).thenReturn(household);

        householdService.setHouseholdHead(1L, 8L, 99L);

        ArgumentCaptor<HouseholdMember> captor = ArgumentCaptor.forClass(HouseholdMember.class);
        verify(memberRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getRelationship()).isEqualTo("HEAD");
        assertThat(captor.getValue().getResidentId()).isEqualTo(8L);
    }
}
