package gov.brgy.ebms.resident.service;

import gov.brgy.ebms.audit.aspect.Auditable;
import gov.brgy.ebms.numbering.DocumentNumberGenerator;
import gov.brgy.ebms.resident.dto.ResidentRequest;
import gov.brgy.ebms.resident.dto.ResidentResponse;
import gov.brgy.ebms.resident.entity.Resident;
import gov.brgy.ebms.resident.repository.ResidentRepository;
import gov.brgy.ebms.security.SecurityUtils;
import gov.brgy.ebms.security.entity.User;
import gov.brgy.ebms.security.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ResidentService {

    private final ResidentRepository residentRepository;
    private final DocumentNumberGenerator documentNumberGenerator;
    private final UserRepository userRepository;

    public ResidentService(ResidentRepository residentRepository,
                           DocumentNumberGenerator documentNumberGenerator,
                           UserRepository userRepository) {
        this.residentRepository = residentRepository;
        this.documentNumberGenerator = documentNumberGenerator;
        this.userRepository = userRepository;
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF')")
    @Transactional(readOnly = true)
    public Page<ResidentResponse> search(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return residentRepository.findAll(pageable).map(ResidentResponse::from);
        }
        return residentRepository.search(query.trim(), pageable).map(ResidentResponse::from);
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF','RESIDENT')")
    @Transactional(readOnly = true)
    public ResidentResponse findById(Long id) {
        Resident resident = findEntityById(id);
        enforceResidentOwnership(resident.getId());
        return ResidentResponse.from(resident);
    }

    @Auditable(entityType = "RESIDENT", action = "CREATE")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF')")
    @Transactional
    public ResidentResponse create(ResidentRequest request, Long createdBy) {
        String dupKey = Resident.buildDupKey(request.firstName(), request.lastName(), request.birthdate());
        List<Resident> duplicates = residentRepository.findByDupKey(dupKey);
        if (!duplicates.isEmpty() && !request.confirmDuplicate()) {
            List<ResidentResponse> candidates = duplicates.stream()
                .map(ResidentResponse::from)
                .toList();
            throw new DuplicateResidentException(
                "A resident with the same name and birthdate already exists.", candidates);
        }

        Resident resident = new Resident();
        resident.setResidentCode(documentNumberGenerator.nextResidentCode());
        resident.setDupKey(dupKey);
        resident.setCreatedBy(createdBy);
        applyRequest(resident, request);

        return ResidentResponse.from(residentRepository.save(resident));
    }

    @Auditable(entityType = "RESIDENT", action = "UPDATE",
               entityClass = Resident.class, entityIdArgIndex = 0)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF')")
    @Transactional
    public ResidentResponse update(Long id, ResidentRequest request, Long updatedBy) {
        Resident resident = findEntityById(id);

        String newDupKey = Resident.buildDupKey(request.firstName(), request.lastName(), request.birthdate());
        if (!newDupKey.equals(resident.getDupKey())) {
            List<Resident> duplicates = residentRepository.findByDupKey(newDupKey);
            if (!duplicates.isEmpty()) {
                List<ResidentResponse> candidates = duplicates.stream()
                    .map(ResidentResponse::from)
                    .toList();
                throw new DuplicateResidentException(
                    "A resident with the same name and birthdate already exists.", candidates);
            }
            resident.setDupKey(newDupKey);
        }

        resident.setUpdatedBy(updatedBy);
        applyRequest(resident, request);
        return ResidentResponse.from(residentRepository.save(resident));
    }

    @Auditable(entityType = "RESIDENT", action = "DELETE",
               entityClass = Resident.class, entityIdArgIndex = 0)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN')")
    @Transactional
    public void delete(Long id, Long deletedBy) {
        Resident resident = findEntityById(id);
        resident.softDelete(deletedBy);
        residentRepository.save(resident);
    }

    /**
     * Restores a soft-deleted resident (sets deleted_at = null).
     * Uses a native query to bypass the @SQLRestriction("deleted_at IS NULL") filter.
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN')")
    @Transactional
    public ResidentResponse restore(Long id, Long updatedBy) {
        Resident resident = residentRepository.findByIdIncludeDeleted(id)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                "Resident not found: " + id));
        resident.setDeletedAt(null);
        resident.setDeletedBy(null);
        resident.setUpdatedBy(updatedBy);
        return ResidentResponse.from(residentRepository.save(resident));
    }

    /**
     * Ownership check for RESIDENT-role callers: a resident user may only access
     * their own record. Staff and admin roles are not restricted.
     *
     * <p>When no authentication context is present (e.g. unit tests without a
     * SecurityContext) the check is skipped entirely, so tests that don't set up
     * Spring Security stubs are not affected.
     */
    private void enforceResidentOwnership(Long entityResidentId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return;

        boolean isResident = auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_RESIDENT".equals(a.getAuthority()));
        if (!isResident) return; // staff/admin roles bypass ownership check

        Long callerId = SecurityUtils.getAuthenticatedUserId();
        // SEC-FIX-2: never silently skip for RESIDENT callers — missing identity = denied
        if (callerId == null) {
            throw new AccessDeniedException(
                "Unable to determine caller identity; access denied for RESIDENT");
        }

        User caller = userRepository.findById(callerId)
            .orElseThrow(() -> new AccessDeniedException("Authenticated user not found"));
        if (!entityResidentId.equals(caller.getResidentId())) {
            throw new AccessDeniedException(
                "Access denied: residents may only access their own records");
        }
    }

    private Resident findEntityById(Long id) {
        return residentRepository.findById(id)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Resident not found: " + id));
    }

    private void applyRequest(Resident resident, ResidentRequest req) {
        resident.setFirstName(req.firstName());
        resident.setMiddleName(req.middleName());
        resident.setLastName(req.lastName());
        resident.setSuffix(req.suffix());
        resident.setBirthdate(req.birthdate());
        resident.setSex(req.sex());
        resident.setCivilStatus(req.civilStatus() != null ? req.civilStatus() : Resident.CivilStatus.SINGLE);
        resident.setContactNumber(req.contactNumber());
        resident.setEmail(req.email());
        resident.setHouseNo(req.houseNo());
        resident.setStreet(req.street());
        resident.setPurokSitio(req.purokSitio());
        resident.setHouseholdId(req.householdId());
        resident.setOccupation(req.occupation());
        resident.setVoter(req.isVoter());
    }

    public static class DuplicateResidentException extends RuntimeException {
        private final List<ResidentResponse> candidates;

        public DuplicateResidentException(String message, List<ResidentResponse> candidates) {
            super(message);
            this.candidates = candidates;
        }

        public List<ResidentResponse> getCandidates() {
            return candidates;
        }
    }
}
