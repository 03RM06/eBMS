package gov.brgy.ebms.api;

import gov.brgy.ebms.resident.dto.ResidentRequest;
import gov.brgy.ebms.resident.dto.ResidentResponse;
import gov.brgy.ebms.resident.service.ResidentService;
import gov.brgy.ebms.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/residents")
public class ResidentController {

    private final ResidentService residentService;

    public ResidentController(ResidentService residentService) {
        this.residentService = residentService;
    }

    @GetMapping
    public ResponseEntity<Page<ResidentResponse>> list(
        @RequestParam(required = false) String q,
        Pageable pageable
    ) {
        return ResponseEntity.ok(residentService.search(q, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResidentResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(residentService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ResidentResponse> create(
        @Valid @RequestBody ResidentRequest request
    ) {
        ResidentResponse response = residentService.create(request, SecurityUtils.getAuthenticatedUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResidentResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody ResidentRequest request
    ) {
        return ResponseEntity.ok(residentService.update(id, request, SecurityUtils.getAuthenticatedUserId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        residentService.delete(id, SecurityUtils.getAuthenticatedUserId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Restores a soft-deleted resident (sets deleted_at = null).
     * Requires BARANGAY_CAPTAIN or SUPER_ADMIN.
     */
    @PostMapping("/{id}/restore")
    public ResponseEntity<ResidentResponse> restore(@PathVariable Long id) {
        return ResponseEntity.ok(
            residentService.restore(id, SecurityUtils.getAuthenticatedUserId())
        );
    }
}
