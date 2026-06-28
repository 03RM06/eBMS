package gov.brgy.ebms.api;

import gov.brgy.ebms.complaint.dto.ComplaintFilingRequest;
import gov.brgy.ebms.complaint.dto.ComplaintResponse;
import gov.brgy.ebms.complaint.entity.Complaint.ComplaintStatus;
import gov.brgy.ebms.complaint.service.ComplaintService;
import gov.brgy.ebms.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/complaints")
public class ComplaintController {

    private final ComplaintService complaintService;

    public ComplaintController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    @PostMapping
    public ResponseEntity<ComplaintResponse> file(@Valid @RequestBody ComplaintFilingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(complaintService.file(request, SecurityUtils.getAuthenticatedUserId()));
    }

    @GetMapping
    public ResponseEntity<Page<ComplaintResponse>> list(
        @RequestParam(required = false) ComplaintStatus status,
        Pageable pageable
    ) {
        return ResponseEntity.ok(complaintService.list(status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ComplaintResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(complaintService.findById(id));
    }

    @PostMapping("/{id}/transition")
    public ResponseEntity<ComplaintResponse> transition(
        @PathVariable Long id,
        @RequestParam ComplaintStatus newStatus,
        @RequestParam(required = false) String note
    ) {
        return ResponseEntity.ok(
            complaintService.transition(id, newStatus, note, SecurityUtils.getAuthenticatedUserId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        complaintService.delete(id, SecurityUtils.getAuthenticatedUserId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns complaints where the given resident is a party and status is
     * FILED or UNDER_MEDIATION (i.e. unresolved). Requires STAFF or above.
     */
    @GetMapping("/unresolved")
    public ResponseEntity<List<ComplaintResponse>> unresolved(
        @RequestParam Long residentId
    ) {
        return ResponseEntity.ok(complaintService.findUnresolved(residentId));
    }
}
