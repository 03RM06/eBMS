package gov.brgy.ebms.api;

import gov.brgy.ebms.household.dto.AddMemberRequest;
import gov.brgy.ebms.household.dto.HouseholdRequest;
import gov.brgy.ebms.household.dto.HouseholdResponse;
import gov.brgy.ebms.household.dto.SetHeadRequest;
import gov.brgy.ebms.household.service.HouseholdService;
import gov.brgy.ebms.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/households")
public class HouseholdController {

    private final HouseholdService householdService;

    public HouseholdController(HouseholdService householdService) {
        this.householdService = householdService;
    }

    @GetMapping
    public ResponseEntity<Page<HouseholdResponse>> list(Pageable pageable) {
        return ResponseEntity.ok(householdService.listAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<HouseholdResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(householdService.findById(id));
    }

    @PostMapping
    public ResponseEntity<HouseholdResponse> create(@Valid @RequestBody HouseholdRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(householdService.create(request, SecurityUtils.getAuthenticatedUserId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<HouseholdResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody HouseholdRequest request
    ) {
        return ResponseEntity.ok(householdService.update(id, request, SecurityUtils.getAuthenticatedUserId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        householdService.delete(id, SecurityUtils.getAuthenticatedUserId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Adds a member to a household. Requires SECRETARY or above.
     */
    @PostMapping("/{id}/members")
    public ResponseEntity<Void> addMember(
        @PathVariable Long id,
        @Valid @RequestBody AddMemberRequest request
    ) {
        householdService.addMember(
            id, request.residentId(), request.relationship(),
            SecurityUtils.getAuthenticatedUserId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Soft-deletes a household member. Requires SECRETARY or above.
     */
    @DeleteMapping("/{id}/members/{residentId}")
    public ResponseEntity<Void> removeMember(
        @PathVariable Long id,
        @PathVariable Long residentId
    ) {
        householdService.removeMember(id, residentId, SecurityUtils.getAuthenticatedUserId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Sets the household head resident. Requires SECRETARY or above.
     */
    @PutMapping("/{id}/head")
    public ResponseEntity<HouseholdResponse> setHead(
        @PathVariable Long id,
        @Valid @RequestBody SetHeadRequest request
    ) {
        return ResponseEntity.ok(
            householdService.setHouseholdHead(id, request.residentId(),
                SecurityUtils.getAuthenticatedUserId())
        );
    }
}
