package gov.brgy.ebms.api;

import gov.brgy.ebms.clearance.dto.ClearanceRequestDto;
import gov.brgy.ebms.clearance.dto.ClearanceResponse;
import gov.brgy.ebms.clearance.entity.ClearanceDocument;
import gov.brgy.ebms.clearance.entity.ClearanceRequest.ClearanceStatus;
import gov.brgy.ebms.clearance.service.ClearanceService;
import gov.brgy.ebms.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/v1/clearances")
public class ClearanceController {

    private final ClearanceService clearanceService;

    @Value("${document.storage.path:./documents}")
    private String storagePath;

    public ClearanceController(ClearanceService clearanceService) {
        this.clearanceService = clearanceService;
    }

    @PostMapping
    public ResponseEntity<ClearanceResponse> submit(@Valid @RequestBody ClearanceRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clearanceService.submit(request, SecurityUtils.getAuthenticatedUserId()));
    }

    @GetMapping
    public ResponseEntity<Page<ClearanceResponse>> list(
        @RequestParam(required = false) ClearanceStatus status,
        Pageable pageable
    ) {
        return ResponseEntity.ok(clearanceService.listByStatus(status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClearanceResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(clearanceService.findById(id));
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<ClearanceResponse> startReview(@PathVariable Long id) {
        return ResponseEntity.ok(clearanceService.startReview(id, SecurityUtils.getAuthenticatedUserId()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ClearanceResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(clearanceService.approve(id, SecurityUtils.getAuthenticatedUserId()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ClearanceResponse> reject(
        @PathVariable Long id,
        @RequestParam(required = false) String remarks
    ) {
        return ResponseEntity.ok(clearanceService.reject(id, remarks, SecurityUtils.getAuthenticatedUserId()));
    }

    /**
     * Streams the approved clearance PDF as an attachment.
     * Requires STAFF+ or the owner RESIDENT.
     * Returns 404 if the clearance has not been approved / no document generated.
     */
    @GetMapping("/{id}/document")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id) {
        ClearanceDocument doc = clearanceService.getDocument(id);
        Path storageRoot = Paths.get(storagePath).toAbsolutePath().normalize();
        Path resolved = Paths.get(doc.getFilePath()).toAbsolutePath().normalize();
        if (!resolved.startsWith(storageRoot)) {
            return ResponseEntity.notFound().build();
        }
        File file = resolved.toFile();
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        // Re-check after resolving symlinks so a symlink inside storage root
        // cannot escape to a path outside it.
        try {
            if (!resolved.toRealPath().startsWith(storageRoot.toRealPath())) {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
        FileSystemResource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"clearance-" + doc.getControlNumber() + ".pdf\"")
            .body(resource);
    }
}
