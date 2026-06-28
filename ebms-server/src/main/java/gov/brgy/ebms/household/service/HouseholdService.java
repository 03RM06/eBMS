package gov.brgy.ebms.household.service;

import gov.brgy.ebms.household.dto.HouseholdRequest;
import gov.brgy.ebms.household.dto.HouseholdResponse;
import gov.brgy.ebms.household.entity.Household;
import gov.brgy.ebms.household.entity.HouseholdMember;
import gov.brgy.ebms.household.repository.HouseholdMemberRepository;
import gov.brgy.ebms.household.repository.HouseholdRepository;
import gov.brgy.ebms.numbering.DocumentNumberGenerator;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HouseholdService {

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository memberRepository;
    private final DocumentNumberGenerator documentNumberGenerator;

    public HouseholdService(
        HouseholdRepository householdRepository,
        HouseholdMemberRepository memberRepository,
        DocumentNumberGenerator documentNumberGenerator
    ) {
        this.householdRepository = householdRepository;
        this.memberRepository = memberRepository;
        this.documentNumberGenerator = documentNumberGenerator;
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF')")
    @Transactional(readOnly = true)
    public Page<HouseholdResponse> listAll(Pageable pageable) {
        return householdRepository.findAll(pageable).map(HouseholdResponse::from);
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF')")
    @Transactional(readOnly = true)
    public HouseholdResponse findById(Long id) {
        return HouseholdResponse.from(findEntityById(id));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF')")
    @Transactional
    public HouseholdResponse create(HouseholdRequest request, Long createdBy) {
        if (request.headResidentId() != null && householdRepository.existsByHeadResidentId(request.headResidentId())) {
            throw new IllegalArgumentException("Resident is already the head of another household.");
        }

        Household household = new Household();
        household.setHouseholdCode(documentNumberGenerator.nextHouseholdCode());
        household.setHeadResidentId(request.headResidentId());
        household.setHouseNo(request.houseNo());
        household.setStreet(request.street());
        household.setPurokSitio(request.purokSitio());
        household.setCreatedBy(createdBy);
        household = householdRepository.save(household);

        if (request.headResidentId() != null) {
            memberRepository.save(new HouseholdMember(
                household.getId(), request.headResidentId(), "HEAD", createdBy
            ));
        }

        return HouseholdResponse.from(household);
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF')")
    @Transactional
    public HouseholdResponse update(Long id, HouseholdRequest request, Long updatedBy) {
        Household household = findEntityById(id);

        if (request.headResidentId() != null
            && !request.headResidentId().equals(household.getHeadResidentId())
            && householdRepository.existsByHeadResidentId(request.headResidentId())) {
            throw new IllegalArgumentException("Resident is already the head of another household.");
        }

        household.setHeadResidentId(request.headResidentId());
        household.setHouseNo(request.houseNo());
        household.setStreet(request.street());
        household.setPurokSitio(request.purokSitio());
        household.setUpdatedBy(updatedBy);
        return HouseholdResponse.from(householdRepository.save(household));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY')")
    @Transactional
    public void addMember(Long householdId, Long residentId, String relationship, Long createdBy) {
        findEntityById(householdId);
        if (memberRepository.findByHouseholdIdAndResidentId(householdId, residentId).isPresent()) {
            throw new IllegalArgumentException("Resident is already a member of this household.");
        }
        memberRepository.save(new HouseholdMember(householdId, residentId, relationship, createdBy));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY')")
    @Transactional
    public void removeMember(Long householdId, Long residentId, Long deletedBy) {
        HouseholdMember member = memberRepository
            .findByHouseholdIdAndResidentId(householdId, residentId)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                "Member not found in household " + householdId + " for resident " + residentId));
        member.softDelete(deletedBy);
        memberRepository.save(member);
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY')")
    @Transactional
    public HouseholdResponse setHouseholdHead(Long householdId, Long residentId, Long updatedBy) {
        if (residentId == null) {
            throw new IllegalArgumentException("residentId must not be null when setting household head");
        }
        Household household = findEntityById(householdId);

        // Guard: prevent a resident from heading two households simultaneously
        if (!residentId.equals(household.getHeadResidentId())
                && householdRepository.existsByHeadResidentId(residentId)) {
            throw new IllegalArgumentException("Resident is already the head of another household.");
        }

        // Demote previous head's member row to MEMBER when switching to a different resident
        Long previousHeadId = household.getHeadResidentId();
        if (previousHeadId != null && !previousHeadId.equals(residentId)) {
            memberRepository.findByHouseholdIdAndResidentId(householdId, previousHeadId)
                .ifPresent(m -> {
                    m.setRelationship("MEMBER");
                    memberRepository.save(m);
                });
        }

        // Upsert new head: update existing member row or create one if absent
        memberRepository.findByHouseholdIdAndResidentId(householdId, residentId)
            .ifPresentOrElse(
                m -> {
                    m.setRelationship("HEAD");
                    memberRepository.save(m);
                },
                () -> memberRepository.save(new HouseholdMember(householdId, residentId, "HEAD", updatedBy))
            );

        household.setHeadResidentId(residentId);
        household.setUpdatedBy(updatedBy);
        return HouseholdResponse.from(householdRepository.save(household));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN')")
    @Transactional
    public void delete(Long id, Long deletedBy) {
        Household household = findEntityById(id);
        household.softDelete(deletedBy);
        householdRepository.save(household);
    }

    private Household findEntityById(Long id) {
        return householdRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Household not found: " + id));
    }
}
