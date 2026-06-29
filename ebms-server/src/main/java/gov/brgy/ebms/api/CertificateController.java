package gov.brgy.ebms.api;

import gov.brgy.ebms.certificate.dto.CertificateRequest;
import gov.brgy.ebms.certificate.dto.CertificateResponse;
import gov.brgy.ebms.certificate.entity.CertificateStatus;
import gov.brgy.ebms.certificate.entity.CertificateType;
import gov.brgy.ebms.certificate.service.CertificateService;
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
@RequestMapping("/api/v1/certificates")
public class CertificateController {

    private final CertificateService certificateService;

    @Value("${document.storage.path:./documents}")
    private String storagePath;

    public CertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @PostMapping
    public ResponseEntity<CertificateResponse> submit(@Valid @RequestBody CertificateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(certificateService.submit(request, SecurityUtils.getAuthenticatedUserId()));
    }

    @GetMapping
    public ResponseEntity<Page<CertificateResponse>> list(
        @RequestParam(required = false) CertificateType type,
        @RequestParam(required = false) CertificateStatus status,
        Pageable pageable
    ) {
        return ResponseEntity.ok(certificateService.listAll(type, status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CertificateResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(certificateService.findById(id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<CertificateResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(certificateService.approve(id, SecurityUtils.getAuthenticatedUserId()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<CertificateResponse> reject(
        @PathVariable Long id,
        @RequestParam(required = false) String remarks
    ) {
        return ResponseEntity.ok(
            certificateService.reject(id, remarks, SecurityUtils.getAuthenticatedUserId()));
    }

    @GetMapping("/{id}/document")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id) {
        String filePath = certificateService.getFilePath(id);
        Path storageRoot = Paths.get(storagePath).toAbsolutePath().normalize();
        Path resolved = Paths.get(filePath).toAbsolutePath().normalize();
        if (!resolved.startsWith(storageRoot)) {
            return ResponseEntity.notFound().build();
        }
        File file = resolved.toFile();
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        try {
            if (!resolved.toRealPath().startsWith(storageRoot.toRealPath())) {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"certificate-" + id + ".pdf\"")
            .body(new FileSystemResource(file));
    }
}
