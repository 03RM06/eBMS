# eBMS Extensions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a certificate generation module, standardize API error responses, and expose user/role management endpoints to the eBMS Spring Boot REST API.

**Architecture:** Three independent, shippable modules — error envelope (GlobalExceptionHandler via `@RestControllerAdvice`), certificate CRUD + PDF generation mirroring the existing clearance pattern, and user management reusing existing User/Role entities. Each module can be merged and deployed independently.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Spring Data JPA, Spring Security 6, OpenPDF 2.0.3, Flyway 10, MySQL 8, JUnit 5, Mockito, AssertJ.

## Global Constraints

- Project root: `C:\Users\USER\Documents\GitHub\eBMS\ebms-server`
- All source files use package `gov.brgy.ebms.*`
- Maven is bundled inside NetBeans — if `mvn` is not in PATH, locate it with: `Get-ChildItem "C:\Program Files" -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1 FullName` then use that full path for every `mvn` command below.
- Use `LocalDateTime` (not `Instant`) — matches existing entity pattern.
- Entities use explicit getters/setters (no Lombok), no-arg constructor, `@PreUpdate` method for `updatedAt`.
- DTOs are Java records with a `static from(Entity)` factory method.
- Controllers live in `gov.brgy.ebms.api`; PDF generators live in `gov.brgy.ebms.pdf`.
- Current highest Flyway migration: `V4__username_unique.sql` — next migration is **V5**.
- `@Auditable` annotation: `gov.brgy.ebms.audit.aspect.Auditable`
- Test style: `@ExtendWith(MockitoExtension.class)`, `@InjectMocks`, AssertJ `assertThat(...)`.

---

### Task 1: Write and commit the design spec

**Files:**
- Create: `docs/superpowers/specs/2026-06-29-certificate-error-usermgmt-design.md`

**Interfaces:**
- Produces: committed design doc (no code interfaces)

- [ ] **Step 1: Copy the approved plan into the spec doc**

```powershell
Copy-Item "C:\Users\USER\.claude\plans\structured-enchanting-pascal.md" `
  "docs\superpowers\specs\2026-06-29-certificate-error-usermgmt-design.md"
```

- [ ] **Step 2: Commit**

```
git -C "C:\Users\USER\Documents\GitHub\eBMS" add ebms-server/docs/
git -C "C:\Users\USER\Documents\GitHub\eBMS" commit -m "docs: add design spec for certificate module, error envelope, user management"
```

Expected output: `1 file changed, N insertions(+)`

---

### Task 2: Global error envelope

**Files:**
- Create: `src/main/java/gov/brgy/ebms/config/GlobalExceptionHandler.java`
- Create: `src/test/java/gov/brgy/ebms/config/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Produces: `ApiErrorResponse` record (used by every downstream task's error assertions)

- [ ] **Step 1: Write the failing test**

Create `src/test/java/gov/brgy/ebms/config/GlobalExceptionHandlerTest.java`:

```java
package gov.brgy.ebms.config;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = GlobalExceptionHandlerTest.StubController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mvc;

    @RestController
    @RequestMapping("/test-stub")
    static class StubController {
        record Body(@NotBlank String name) {}

        @PostMapping("/validate")
        void validate(@Valid @RequestBody Body body) {}

        @GetMapping("/not-found")
        void notFound() { throw new EntityNotFoundException("Item not found"); }

        @GetMapping("/conflict")
        void conflict() { throw new IllegalStateException("Invalid status transition"); }

        @GetMapping("/error")
        void error() { throw new RuntimeException("Internal details"); }
    }

    @Test
    void validationError_returns400WithFieldErrors() throws Exception {
        mvc.perform(post("/test-stub/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Validation failed"))
            .andExpect(jsonPath("$.errors[0]").value("name: must not be blank"))
            .andExpect(jsonPath("$.path").value("/test-stub/validate"));
    }

    @Test
    void entityNotFound_returns404WithMessage() throws Exception {
        mvc.perform(get("/test-stub/not-found"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Item not found"));
    }

    @Test
    void illegalState_returns409WithMessage() throws Exception {
        mvc.perform(get("/test-stub/conflict"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Invalid status transition"));
    }

    @Test
    void genericException_returns500WithGenericMessage_noInternalDetails() throws Exception {
        mvc.perform(get("/test-stub/error"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```
mvn test -pl ebms-server -Dtest=GlobalExceptionHandlerTest -q
```

Expected: `BUILD FAILURE` — `GlobalExceptionHandler` does not exist yet.

- [ ] **Step 3: Write the implementation**

Create `src/main/java/gov/brgy/ebms/config/GlobalExceptionHandler.java`:

```java
package gov.brgy.ebms.config;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ApiErrorResponse(
        boolean success,
        String message,
        List<String> errors,
        LocalDateTime timestamp,
        String path
    ) {}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .toList();
        return ResponseEntity.badRequest()
            .body(new ApiErrorResponse(false, "Validation failed", errors,
                LocalDateTime.now(), req.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest()
            .body(new ApiErrorResponse(false, "Malformed request body", List.of(),
                LocalDateTime.now(), req.getRequestURI()));
    }

    @ExceptionHandler({EntityNotFoundException.class, NoSuchElementException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            RuntimeException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ApiErrorResponse(false, ex.getMessage(), List.of(),
                LocalDateTime.now(), req.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ApiErrorResponse(false, "Access denied", List.of(),
                LocalDateTime.now(), req.getRequestURI()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            AuthenticationException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ApiErrorResponse(false, "Authentication required", List.of(),
                LocalDateTime.now(), req.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
            DataIntegrityViolationException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiErrorResponse(false, "Conflict: duplicate or constraint violation",
                List.of(), LocalDateTime.now(), req.getRequestURI()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(
            IllegalStateException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiErrorResponse(false, ex.getMessage(), List.of(),
                LocalDateTime.now(), req.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiErrorResponse(false, "An unexpected error occurred", List.of(),
                LocalDateTime.now(), req.getRequestURI()));
    }
}
```

- [ ] **Step 4: Run to verify it passes**

```
mvn test -pl ebms-server -Dtest=GlobalExceptionHandlerTest -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```
git add src/main/java/gov/brgy/ebms/config/GlobalExceptionHandler.java \
        src/test/java/gov/brgy/ebms/config/GlobalExceptionHandlerTest.java
git commit -m "feat: add global error envelope via @RestControllerAdvice"
```

---

### Task 3: Certificate scaffolding — entity, enums, repository, migration, sequence

**Files:**
- Create: `src/main/java/gov/brgy/ebms/certificate/entity/Certificate.java`
- Create: `src/main/java/gov/brgy/ebms/certificate/entity/CertificateType.java`
- Create: `src/main/java/gov/brgy/ebms/certificate/entity/CertificateStatus.java`
- Create: `src/main/java/gov/brgy/ebms/certificate/repository/CertificateRepository.java`
- Create: `src/main/resources/db/migration/V5__add_certificates_table.sql`
- Modify: `src/main/java/gov/brgy/ebms/numbering/DocumentNumberGenerator.java`
- Modify: `src/main/java/gov/brgy/ebms/numbering/SequenceService.java`

**Interfaces:**
- Produces:
  - `Certificate` entity (used by Tasks 4–6)
  - `CertificateRepository` (injected in Task 5 service)
  - `DocumentNumberGenerator.nextCertificateNumber()` (injected in Task 5 service)

- [ ] **Step 1: Create `CertificateType` enum**

Create `src/main/java/gov/brgy/ebms/certificate/entity/CertificateType.java`:

```java
package gov.brgy.ebms.certificate.entity;

public enum CertificateType {
    INDIGENCY,
    RESIDENCY,
    BUSINESS_PERMIT_ENDORSEMENT,
    GOOD_MORAL
}
```

- [ ] **Step 2: Create `CertificateStatus` enum**

Create `src/main/java/gov/brgy/ebms/certificate/entity/CertificateStatus.java`:

```java
package gov.brgy.ebms.certificate.entity;

public enum CertificateStatus {
    REQUESTED,
    APPROVED,
    REJECTED
}
```

- [ ] **Step 3: Create `Certificate` entity**

Create `src/main/java/gov/brgy/ebms/certificate/entity/Certificate.java`:

```java
package gov.brgy.ebms.certificate.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "certificates")
@SQLRestriction("deleted_at IS NULL")
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "control_number", unique = true, length = 32)
    private String controlNumber;

    @Column(name = "resident_id", nullable = false)
    private Long residentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "certificate_type", nullable = false, length = 32)
    private CertificateType certificateType;

    @Column(nullable = false, length = 255)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CertificateStatus status = CertificateStatus.REQUESTED;

    @Column(length = 500)
    private String remarks;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "sha256_checksum", length = 64)
    private String sha256Checksum;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    public Certificate() {}

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void softDelete(Long deletedByUserId) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedByUserId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getControlNumber() { return controlNumber; }
    public void setControlNumber(String controlNumber) { this.controlNumber = controlNumber; }
    public Long getResidentId() { return residentId; }
    public void setResidentId(Long residentId) { this.residentId = residentId; }
    public CertificateType getCertificateType() { return certificateType; }
    public void setCertificateType(CertificateType certificateType) { this.certificateType = certificateType; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public CertificateStatus getStatus() { return status; }
    public void setStatus(CertificateStatus status) { this.status = status; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getSha256Checksum() { return sha256Checksum; }
    public void setSha256Checksum(String sha256Checksum) { this.sha256Checksum = sha256Checksum; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public Long getDeletedBy() { return deletedBy; }
}
```

- [ ] **Step 4: Create `CertificateRepository`**

Create `src/main/java/gov/brgy/ebms/certificate/repository/CertificateRepository.java`:

```java
package gov.brgy.ebms.certificate.repository;

import gov.brgy.ebms.certificate.entity.Certificate;
import gov.brgy.ebms.certificate.entity.CertificateStatus;
import gov.brgy.ebms.certificate.entity.CertificateType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    Page<Certificate> findByStatus(CertificateStatus status, Pageable pageable);
    Page<Certificate> findByCertificateType(CertificateType type, Pageable pageable);
    Page<Certificate> findByStatusAndCertificateType(CertificateStatus status, CertificateType type, Pageable pageable);
    Page<Certificate> findByResidentId(Long residentId, Pageable pageable);
}
```

- [ ] **Step 5: Create Flyway migration V5**

Create `src/main/resources/db/migration/V5__add_certificates_table.sql`:

```sql
-- V5__add_certificates_table.sql

CREATE TABLE certificates (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  control_number    VARCHAR(32)   NOT NULL UNIQUE,
  resident_id       BIGINT        NOT NULL,
  certificate_type  VARCHAR(32)   NOT NULL,
  purpose           VARCHAR(255)  NOT NULL,
  status            VARCHAR(16)   NOT NULL DEFAULT 'REQUESTED',
  remarks           VARCHAR(500)  NULL,
  approved_by       BIGINT        NULL,
  approved_at       TIMESTAMP     NULL,
  file_path         VARCHAR(512)  NULL,
  sha256_checksum   CHAR(64)      NULL,
  created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by        BIGINT        NULL,
  updated_by        BIGINT        NULL,
  deleted_at        TIMESTAMP     NULL,
  deleted_by        BIGINT        NULL,
  INDEX idx_cert_resident  (resident_id),
  INDEX idx_cert_status    (status),
  INDEX idx_cert_deleted   (deleted_at),
  CONSTRAINT fk_cert_resident FOREIGN KEY (resident_id) REFERENCES residents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 6: Add `nextCertificateNumber()` to the `DocumentNumberGenerator` interface**

Edit `src/main/java/gov/brgy/ebms/numbering/DocumentNumberGenerator.java` — add the last line:

```java
package gov.brgy.ebms.numbering;

public interface DocumentNumberGenerator {
    String nextResidentCode();
    String nextHouseholdCode();
    String nextClearanceNumber();
    String nextBlotterNumber();
    String nextOrReference();
    String nextCertificateNumber();
}
```

- [ ] **Step 7: Implement `nextCertificateNumber()` in `SequenceService`**

Add this method to `src/main/java/gov/brgy/ebms/numbering/SequenceService.java` after `nextOrReference()`:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public String nextCertificateNumber() {
    return format("CERT", barangayPrefix + "-CERT-{YYYY}-{NNNNNN}");
}
```

- [ ] **Step 8: Verify the project compiles**

```
mvn compile -pl ebms-server -q
```

Expected: `BUILD SUCCESS` — no compile errors.

- [ ] **Step 9: Commit**

```
git add src/main/java/gov/brgy/ebms/certificate/ \
        src/main/java/gov/brgy/ebms/numbering/DocumentNumberGenerator.java \
        src/main/java/gov/brgy/ebms/numbering/SequenceService.java \
        src/main/resources/db/migration/V5__add_certificates_table.sql
git commit -m "feat: add Certificate entity, repository, V5 migration, nextCertificateNumber"
```

---

### Task 4: CertificatePdfGenerator

**Files:**
- Create: `src/main/java/gov/brgy/ebms/pdf/CertificatePdfGenerator.java`
- Create: `src/test/java/gov/brgy/ebms/pdf/CertificatePdfGeneratorTest.java`

**Interfaces:**
- Consumes: `Certificate` (Task 3), `Resident` entity (existing at `gov.brgy.ebms.resident.entity.Resident`)
- Produces: `CertificatePdfGenerator.generate(Certificate, Resident)` returning `ClearancePdfGenerator.PdfGenerationResult`

> **Certificate wording:** Before going to production, replace the placeholder body text in `bodyFor()` with approved official barangay text for each certificate type. Consult an actual barangay clearance/indigency/residency template.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/gov/brgy/ebms/pdf/CertificatePdfGeneratorTest.java`:

```java
package gov.brgy.ebms.pdf;

import gov.brgy.ebms.certificate.entity.Certificate;
import gov.brgy.ebms.certificate.entity.CertificateType;
import gov.brgy.ebms.resident.entity.Resident;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CertificatePdfGeneratorTest {

    @TempDir
    Path tempDir;

    private CertificatePdfGenerator generator() {
        CertificatePdfGenerator gen = new CertificatePdfGenerator();
        ReflectionTestUtils.setField(gen, "storagePath", tempDir.toString());
        ReflectionTestUtils.setField(gen, "barangayName", "Test Barangay");
        return gen;
    }

    private Resident sampleResident() {
        Resident r = new Resident();
        r.setFirstName("Juan");
        r.setMiddleName("Santos");
        r.setLastName("Dela Cruz");
        r.setSuffix(null);
        return r;
    }

    private Certificate sampleCertificate(CertificateType type) {
        Certificate c = new Certificate();
        c.setControlNumber("TEST-CERT-2026-000001");
        c.setCertificateType(type);
        c.setPurpose("For employment");
        return c;
    }

    @ParameterizedTest
    @EnumSource(CertificateType.class)
    void generate_allTypes_producesNonEmptyPdf(CertificateType type) throws IOException {
        ClearancePdfGenerator.PdfGenerationResult result =
            generator().generate(sampleCertificate(type), sampleResident());

        assertThat(result.filePath()).isNotBlank();
        assertThat(new java.io.File(result.filePath())).exists().isNotEmpty();
        assertThat(result.sha256Checksum()).hasSize(64);
    }

    @Test
    void generate_fileNameContainsControlNumber() throws IOException {
        ClearancePdfGenerator.PdfGenerationResult result =
            generator().generate(sampleCertificate(CertificateType.INDIGENCY), sampleResident());

        assertThat(result.filePath()).contains("TEST-CERT-2026-000001");
    }
}
```

- [ ] **Step 2: Run to verify they fail**

```
mvn test -pl ebms-server -Dtest=CertificatePdfGeneratorTest -q
```

Expected: `BUILD FAILURE` — `CertificatePdfGenerator` does not exist yet.

- [ ] **Step 3: Write the implementation**

Create `src/main/java/gov/brgy/ebms/pdf/CertificatePdfGenerator.java`:

```java
package gov.brgy.ebms.pdf;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import gov.brgy.ebms.certificate.entity.Certificate;
import gov.brgy.ebms.certificate.entity.CertificateType;
import gov.brgy.ebms.resident.entity.Resident;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

@Component
public class CertificatePdfGenerator {

    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("MMMM dd, yyyy");

    @Value("${document.storage.path:./documents}")
    private String storagePath;

    @Value("${barangay.name:Barangay Sample}")
    private String barangayName;

    public ClearancePdfGenerator.PdfGenerationResult generate(
            Certificate certificate, Resident resident) throws IOException {
        Path dir = Paths.get(storagePath, "certificates");
        Files.createDirectories(dir);

        String filename = certificate.getControlNumber().replace("/", "-") + ".pdf";
        Path filePath = dir.resolve(filename);

        try (OutputStream out = new FileOutputStream(filePath.toFile())) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
            Font headerFont = new Font(Font.HELVETICA, 12, Font.BOLD);
            Font normalFont = new Font(Font.HELVETICA, 11, Font.NORMAL);

            document.add(new Paragraph(barangayName, titleFont));
            document.add(new Paragraph(titleFor(certificate.getCertificateType()), titleFont));
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("Control No: " + certificate.getControlNumber(), headerFont));
            document.add(new Paragraph("Date Issued: " + LocalDateTime.now().format(DATE_FORMAT), normalFont));
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("TO WHOM IT MAY CONCERN:", headerFont));
            document.add(Chunk.NEWLINE);

            String fullName = resident.getFirstName()
                + (resident.getMiddleName() != null ? " " + resident.getMiddleName() : "")
                + " " + resident.getLastName()
                + (resident.getSuffix() != null ? " " + resident.getSuffix() : "");

            document.add(new Paragraph(bodyFor(certificate.getCertificateType(), fullName), normalFont));
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("Purpose: " + certificate.getPurpose(), normalFont));
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph(
                "This certification is issued upon the request of the above-named person "
                + "for whatever legal purpose it may serve.",
                normalFont
            ));

            document.close();
        }

        String checksum = sha256File(filePath);
        return new ClearancePdfGenerator.PdfGenerationResult(filePath.toString(), checksum);
    }

    private String titleFor(CertificateType type) {
        return switch (type) {
            case INDIGENCY -> "CERTIFICATE OF INDIGENCY";
            case RESIDENCY -> "CERTIFICATE OF RESIDENCY";
            case BUSINESS_PERMIT_ENDORSEMENT -> "BARANGAY BUSINESS PERMIT ENDORSEMENT";
            case GOOD_MORAL -> "CERTIFICATE OF GOOD MORAL CHARACTER";
        };
    }

    private String bodyFor(CertificateType type, String fullName) {
        // Replace placeholder wording with official barangay-approved text before production use.
        return switch (type) {
            case INDIGENCY ->
                "This is to certify that " + fullName + " is a bonafide resident of this barangay "
                + "and belongs to an indigent family who is in need of assistance.";
            case RESIDENCY ->
                "This is to certify that " + fullName + " is a bonafide resident of this barangay "
                + "and has been residing in this area for a considerable period of time.";
            case BUSINESS_PERMIT_ENDORSEMENT ->
                "This is to certify that " + fullName + " is a bonafide resident of this barangay "
                + "and is hereby endorsed to apply for a business permit with the Municipal/City Office.";
            case GOOD_MORAL ->
                "This is to certify that " + fullName + " is a bonafide resident of this barangay "
                + "and is known to be a person of good moral character and standing in the community.";
        };
    }

    private String sha256File(Path filePath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] hash = digest.digest(fileBytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```

- [ ] **Step 4: Run to verify they pass**

```
mvn test -pl ebms-server -Dtest=CertificatePdfGeneratorTest -q
```

Expected: `BUILD SUCCESS`, 5 tests pass (4 parameterized + 1 filename check).

- [ ] **Step 5: Commit**

```
git add src/main/java/gov/brgy/ebms/pdf/CertificatePdfGenerator.java \
        src/test/java/gov/brgy/ebms/pdf/CertificatePdfGeneratorTest.java
git commit -m "feat: add CertificatePdfGenerator for all 4 certificate types"
```

---

### Task 5: CertificateService + DTOs

**Files:**
- Create: `src/main/java/gov/brgy/ebms/certificate/dto/CertificateRequest.java`
- Create: `src/main/java/gov/brgy/ebms/certificate/dto/CertificateResponse.java`
- Create: `src/main/java/gov/brgy/ebms/certificate/service/CertificateService.java`
- Create: `src/test/java/gov/brgy/ebms/certificate/CertificateServiceTest.java`

**Interfaces:**
- Consumes: `CertificateRepository` (Task 3), `CertificatePdfGenerator` (Task 4), `DocumentNumberGenerator.nextCertificateNumber()` (Task 3), `ResidentRepository` (existing), `UserRepository` (existing)
- Produces:
  - `CertificateService.submit(CertificateRequest, Long createdBy) → CertificateResponse`
  - `CertificateService.listAll(CertificateType, CertificateStatus, Pageable) → Page<CertificateResponse>`
  - `CertificateService.findById(Long) → CertificateResponse`
  - `CertificateService.approve(Long, Long approvedBy) → CertificateResponse`
  - `CertificateService.reject(Long, String remarks, Long updatedBy) → CertificateResponse`
  - `CertificateService.getFilePath(Long) → String`

- [ ] **Step 1: Create the DTOs**

Create `src/main/java/gov/brgy/ebms/certificate/dto/CertificateRequest.java`:

```java
package gov.brgy.ebms.certificate.dto;

import gov.brgy.ebms.certificate.entity.CertificateType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CertificateRequest(
    @NotNull Long residentId,
    @NotNull CertificateType certificateType,
    @NotBlank @Size(max = 255) String purpose
) {}
```

Create `src/main/java/gov/brgy/ebms/certificate/dto/CertificateResponse.java`:

```java
package gov.brgy.ebms.certificate.dto;

import gov.brgy.ebms.certificate.entity.Certificate;

import java.time.LocalDateTime;

public record CertificateResponse(
    Long id,
    String controlNumber,
    Long residentId,
    String certificateType,
    String purpose,
    String status,
    String remarks,
    Long approvedBy,
    LocalDateTime approvedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static CertificateResponse from(Certificate c) {
        return new CertificateResponse(
            c.getId(), c.getControlNumber(), c.getResidentId(),
            c.getCertificateType().name(), c.getPurpose(), c.getStatus().name(),
            c.getRemarks(), c.getApprovedBy(), c.getApprovedAt(),
            c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
```

- [ ] **Step 2: Write the failing tests**

Create `src/test/java/gov/brgy/ebms/certificate/CertificateServiceTest.java`:

```java
package gov.brgy.ebms.certificate;

import gov.brgy.ebms.certificate.dto.CertificateRequest;
import gov.brgy.ebms.certificate.dto.CertificateResponse;
import gov.brgy.ebms.certificate.entity.Certificate;
import gov.brgy.ebms.certificate.entity.CertificateStatus;
import gov.brgy.ebms.certificate.entity.CertificateType;
import gov.brgy.ebms.certificate.repository.CertificateRepository;
import gov.brgy.ebms.certificate.service.CertificateService;
import gov.brgy.ebms.numbering.DocumentNumberGenerator;
import gov.brgy.ebms.pdf.CertificatePdfGenerator;
import gov.brgy.ebms.pdf.ClearancePdfGenerator;
import gov.brgy.ebms.resident.entity.Resident;
import gov.brgy.ebms.resident.repository.ResidentRepository;
import gov.brgy.ebms.security.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    @Mock CertificateRepository certificateRepository;
    @Mock ResidentRepository residentRepository;
    @Mock DocumentNumberGenerator documentNumberGenerator;
    @Mock CertificatePdfGenerator pdfGenerator;
    @Mock UserRepository userRepository;

    @InjectMocks CertificateService certificateService;

    @Test
    void submit_happyPath_returnsCertificateWithRequestedStatus() {
        CertificateRequest req = new CertificateRequest(1L, CertificateType.INDIGENCY, "For scholarship");
        when(residentRepository.findById(1L)).thenReturn(Optional.of(new Resident()));
        when(documentNumberGenerator.nextCertificateNumber()).thenReturn("BRGY-CERT-2026-000001");

        Certificate saved = buildCertificate(1L, 1L, CertificateStatus.REQUESTED);
        saved.setControlNumber("BRGY-CERT-2026-000001");
        when(certificateRepository.save(any())).thenReturn(saved);

        CertificateResponse response = certificateService.submit(req, 99L);

        assertThat(response.status()).isEqualTo("REQUESTED");
        assertThat(response.controlNumber()).isEqualTo("BRGY-CERT-2026-000001");
        verify(documentNumberGenerator).nextCertificateNumber();
    }

    @Test
    void submit_residentNotFound_throwsEntityNotFoundException() {
        when(residentRepository.findById(99L)).thenReturn(Optional.empty());
        CertificateRequest req = new CertificateRequest(99L, CertificateType.INDIGENCY, "Purpose");

        assertThatThrownBy(() -> certificateService.submit(req, 1L))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("99");
    }

    @Test
    void approve_happyPath_setsStatusApprovedAndGeneratesPdf() throws IOException {
        Certificate cert = buildCertificate(1L, 1L, CertificateStatus.REQUESTED);
        cert.setCertificateType(CertificateType.RESIDENCY);
        when(certificateRepository.findById(1L)).thenReturn(Optional.of(cert));
        when(residentRepository.findById(1L)).thenReturn(Optional.of(new Resident()));

        File tmpPdf = File.createTempFile("cert-test", ".pdf");
        tmpPdf.deleteOnExit();
        when(pdfGenerator.generate(any(), any()))
            .thenReturn(new ClearancePdfGenerator.PdfGenerationResult(tmpPdf.getAbsolutePath(), "abc123"));
        when(certificateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CertificateResponse response = certificateService.approve(1L, 5L);

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.approvedBy()).isEqualTo(5L);
    }

    @Test
    void approve_whenNotRequested_throwsIllegalStateException() {
        Certificate cert = buildCertificate(1L, 1L, CertificateStatus.APPROVED);
        when(certificateRepository.findById(1L)).thenReturn(Optional.of(cert));

        assertThatThrownBy(() -> certificateService.approve(1L, 5L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("APPROVED");
    }

    @Test
    void reject_happyPath_setsStatusRejectedWithRemarks() {
        Certificate cert = buildCertificate(1L, 1L, CertificateStatus.REQUESTED);
        when(certificateRepository.findById(1L)).thenReturn(Optional.of(cert));
        when(certificateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CertificateResponse response = certificateService.reject(1L, "Incomplete docs", 5L);

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.remarks()).isEqualTo("Incomplete docs");
    }

    @Test
    void reject_whenAlreadyApproved_throwsIllegalStateException() {
        Certificate cert = buildCertificate(1L, 1L, CertificateStatus.APPROVED);
        when(certificateRepository.findById(1L)).thenReturn(Optional.of(cert));

        assertThatThrownBy(() -> certificateService.reject(1L, "reason", 5L))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getFilePath_whenNotApproved_throwsIllegalStateException() {
        Certificate cert = buildCertificate(1L, 1L, CertificateStatus.REQUESTED);
        when(certificateRepository.findById(1L)).thenReturn(Optional.of(cert));

        assertThatThrownBy(() -> certificateService.getFilePath(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not yet approved");
    }

    private Certificate buildCertificate(Long id, Long residentId, CertificateStatus status) {
        Certificate c = new Certificate();
        c.setId(id);
        c.setResidentId(residentId);
        c.setStatus(status);
        c.setCertificateType(CertificateType.INDIGENCY);
        c.setPurpose("Test purpose");
        c.setControlNumber("BRGY-CERT-2026-" + String.format("%06d", id));
        return c;
    }
}
```

- [ ] **Step 3: Run to verify they fail**

```
mvn test -pl ebms-server -Dtest=CertificateServiceTest -q
```

Expected: `BUILD FAILURE` — `CertificateService` does not exist yet.

- [ ] **Step 4: Write the service**

Create `src/main/java/gov/brgy/ebms/certificate/service/CertificateService.java`:

```java
package gov.brgy.ebms.certificate.service;

import gov.brgy.ebms.audit.aspect.Auditable;
import gov.brgy.ebms.certificate.dto.CertificateRequest;
import gov.brgy.ebms.certificate.dto.CertificateResponse;
import gov.brgy.ebms.certificate.entity.Certificate;
import gov.brgy.ebms.certificate.entity.CertificateStatus;
import gov.brgy.ebms.certificate.entity.CertificateType;
import gov.brgy.ebms.certificate.repository.CertificateRepository;
import gov.brgy.ebms.numbering.DocumentNumberGenerator;
import gov.brgy.ebms.pdf.CertificatePdfGenerator;
import gov.brgy.ebms.pdf.ClearancePdfGenerator;
import gov.brgy.ebms.resident.entity.Resident;
import gov.brgy.ebms.resident.repository.ResidentRepository;
import gov.brgy.ebms.security.SecurityUtils;
import gov.brgy.ebms.security.entity.User;
import gov.brgy.ebms.security.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;

@Service
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final ResidentRepository residentRepository;
    private final DocumentNumberGenerator documentNumberGenerator;
    private final CertificatePdfGenerator pdfGenerator;
    private final UserRepository userRepository;

    public CertificateService(
        CertificateRepository certificateRepository,
        ResidentRepository residentRepository,
        DocumentNumberGenerator documentNumberGenerator,
        CertificatePdfGenerator pdfGenerator,
        UserRepository userRepository
    ) {
        this.certificateRepository = certificateRepository;
        this.residentRepository = residentRepository;
        this.documentNumberGenerator = documentNumberGenerator;
        this.pdfGenerator = pdfGenerator;
        this.userRepository = userRepository;
    }

    @Auditable(entityType = "CERTIFICATE", action = "CREATE")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF','RESIDENT')")
    @Transactional
    public CertificateResponse submit(CertificateRequest request, Long createdBy) {
        enforceResidentOwnership(request.residentId());
        residentRepository.findById(request.residentId())
            .orElseThrow(() -> new EntityNotFoundException("Resident not found: " + request.residentId()));

        Certificate cert = new Certificate();
        cert.setControlNumber(documentNumberGenerator.nextCertificateNumber());
        cert.setResidentId(request.residentId());
        cert.setCertificateType(request.certificateType());
        cert.setPurpose(request.purpose());
        cert.setCreatedBy(createdBy);

        return CertificateResponse.from(certificateRepository.save(cert));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF')")
    @Transactional(readOnly = true)
    public Page<CertificateResponse> listAll(CertificateType type, CertificateStatus status, Pageable pageable) {
        if (type != null && status != null)
            return certificateRepository.findByStatusAndCertificateType(status, type, pageable).map(CertificateResponse::from);
        if (type != null)
            return certificateRepository.findByCertificateType(type, pageable).map(CertificateResponse::from);
        if (status != null)
            return certificateRepository.findByStatus(status, pageable).map(CertificateResponse::from);
        return certificateRepository.findAll(pageable).map(CertificateResponse::from);
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF','RESIDENT')")
    @Transactional(readOnly = true)
    public CertificateResponse findById(Long id) {
        Certificate cert = findEntityById(id);
        enforceResidentOwnership(cert.getResidentId());
        return CertificateResponse.from(cert);
    }

    @Auditable(entityType = "CERTIFICATE", action = "APPROVE",
               entityClass = Certificate.class, entityIdArgIndex = 0)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY')")
    @Transactional
    public CertificateResponse approve(Long id, Long approvedBy) {
        Certificate cert = findEntityById(id);
        if (cert.getStatus() != CertificateStatus.REQUESTED) {
            throw new IllegalStateException(
                "Cannot approve a certificate with status " + cert.getStatus());
        }
        Resident resident = residentRepository.findById(cert.getResidentId())
            .orElseThrow(() -> new EntityNotFoundException("Resident not found"));

        try {
            ClearancePdfGenerator.PdfGenerationResult result = pdfGenerator.generate(cert, resident);
            cert.setFilePath(result.filePath());
            cert.setSha256Checksum(result.sha256Checksum());
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate certificate PDF", e);
        }

        cert.setStatus(CertificateStatus.APPROVED);
        cert.setApprovedBy(approvedBy);
        cert.setApprovedAt(LocalDateTime.now());
        cert.setUpdatedBy(approvedBy);
        return CertificateResponse.from(certificateRepository.save(cert));
    }

    @Auditable(entityType = "CERTIFICATE", action = "REJECT",
               entityClass = Certificate.class, entityIdArgIndex = 0)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY')")
    @Transactional
    public CertificateResponse reject(Long id, String remarks, Long updatedBy) {
        Certificate cert = findEntityById(id);
        if (cert.getStatus() == CertificateStatus.APPROVED) {
            throw new IllegalStateException("Cannot reject an already approved certificate.");
        }
        cert.setStatus(CertificateStatus.REJECTED);
        cert.setRemarks(remarks);
        cert.setUpdatedBy(updatedBy);
        return CertificateResponse.from(certificateRepository.save(cert));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN','SECRETARY','STAFF','RESIDENT')")
    @Transactional(readOnly = true)
    public String getFilePath(Long id) {
        Certificate cert = findEntityById(id);
        enforceResidentOwnership(cert.getResidentId());
        if (cert.getStatus() != CertificateStatus.APPROVED || cert.getFilePath() == null) {
            throw new IllegalStateException("Certificate not yet approved: " + id);
        }
        return cert.getFilePath();
    }

    private void enforceResidentOwnership(Long entityResidentId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return;

        boolean isResident = auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_RESIDENT".equals(a.getAuthority()));
        if (!isResident) return;

        Long callerId = SecurityUtils.getAuthenticatedUserId();
        if (callerId == null) {
            throw new AccessDeniedException("Unable to determine caller identity; access denied for RESIDENT");
        }
        User caller = userRepository.findById(callerId)
            .orElseThrow(() -> new AccessDeniedException("Authenticated user not found"));
        if (!entityResidentId.equals(caller.getResidentId())) {
            throw new AccessDeniedException("Access denied: residents may only access their own records");
        }
    }

    private Certificate findEntityById(Long id) {
        return certificateRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Certificate not found: " + id));
    }
}
```

- [ ] **Step 5: Run to verify they pass**

```
mvn test -pl ebms-server -Dtest=CertificateServiceTest -q
```

Expected: `BUILD SUCCESS`, all 6 tests pass.

- [ ] **Step 6: Commit**

```
git add src/main/java/gov/brgy/ebms/certificate/dto/ \
        src/main/java/gov/brgy/ebms/certificate/service/ \
        src/test/java/gov/brgy/ebms/certificate/CertificateServiceTest.java
git commit -m "feat: add CertificateService with submit/approve/reject/getFilePath"
```

---

### Task 6: CertificateController + path confinement test

**Files:**
- Create: `src/main/java/gov/brgy/ebms/api/CertificateController.java`
- Create: `src/test/java/gov/brgy/ebms/certificate/CertificatePathConfinementTest.java`

**Interfaces:**
- Consumes: `CertificateService` (Task 5), `SecurityUtils.getAuthenticatedUserId()` (existing)
- Produces: REST endpoints at `/api/v1/certificates`

- [ ] **Step 1: Write the path confinement test**

Create `src/test/java/gov/brgy/ebms/certificate/CertificatePathConfinementTest.java`:

```java
package gov.brgy.ebms.certificate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CertificatePathConfinementTest {

    @TempDir
    Path storageRoot;

    @Test
    void symlink_escapingStorageRoot_isDetected() throws IOException {
        Path outside = Files.createTempFile("outside", ".pdf");
        outside.toFile().deleteOnExit();

        Path symlink = storageRoot.resolve("escape.pdf");
        Files.createSymbolicLink(symlink, outside);

        Path resolved = symlink.toAbsolutePath().normalize();
        boolean withinRoot = resolved.startsWith(storageRoot.toAbsolutePath().normalize());
        assertThat(withinRoot).as("Symlink target appears inside root before realPath check").isTrue();

        boolean withinRootAfterRealPath = resolved.toRealPath()
            .startsWith(storageRoot.toRealPath());
        assertThat(withinRootAfterRealPath)
            .as("Symlink must NOT pass the realPath confinement check")
            .isFalse();
    }

    @Test
    void legitimateFile_withinStorageRoot_passes() throws IOException {
        Path legitFile = Files.createTempFile(storageRoot, "cert", ".pdf");
        legitFile.toFile().deleteOnExit();

        Path resolved = legitFile.toAbsolutePath().normalize();
        boolean withinRoot = resolved.startsWith(storageRoot.toAbsolutePath().normalize())
            && resolved.toRealPath().startsWith(storageRoot.toRealPath());
        assertThat(withinRoot).isTrue();
    }
}
```

- [ ] **Step 2: Run to verify they pass** (these tests are self-contained, no implementation needed)

```
mvn test -pl ebms-server -Dtest=CertificatePathConfinementTest -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Write the controller**

Create `src/main/java/gov/brgy/ebms/api/CertificateController.java`:

```java
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
```

- [ ] **Step 4: Run the full test suite**

```
mvn test -pl ebms-server -q
```

Expected: `BUILD SUCCESS` — all tests including the original 17 pass.

- [ ] **Step 5: Commit**

```
git add src/main/java/gov/brgy/ebms/api/CertificateController.java \
        src/test/java/gov/brgy/ebms/certificate/CertificatePathConfinementTest.java
git commit -m "feat: add CertificateController with download path confinement"
```

---

### Task 7: UserManagementService + DTOs

**Files:**
- Create: `src/main/java/gov/brgy/ebms/usermgmt/dto/CreateUserRequest.java`
- Create: `src/main/java/gov/brgy/ebms/usermgmt/dto/UpdateUserRequest.java`
- Create: `src/main/java/gov/brgy/ebms/usermgmt/dto/AssignRolesRequest.java`
- Create: `src/main/java/gov/brgy/ebms/usermgmt/dto/UserDetailResponse.java`
- Create: `src/main/java/gov/brgy/ebms/usermgmt/UserManagementService.java`
- Create: `src/test/java/gov/brgy/ebms/usermgmt/UserManagementServiceTest.java`

**Interfaces:**
- Consumes: `UserRepository` (existing at `gov.brgy.ebms.security.repository.UserRepository`), `RoleRepository` (existing), `PasswordEncoder` bean (defined in `SecurityConfig`)
- Produces:
  - `UserManagementService.createUser(CreateUserRequest) → UserDetailResponse`
  - `UserManagementService.listUsers(Pageable) → Page<UserDetailResponse>`
  - `UserManagementService.getUser(Long) → UserDetailResponse`
  - `UserManagementService.updateUser(Long, UpdateUserRequest) → UserDetailResponse`
  - `UserManagementService.assignRoles(Long, AssignRolesRequest) → UserDetailResponse`
  - `UserManagementService.unlockUser(Long) → UserDetailResponse`

- [ ] **Step 1: Create the DTOs**

Create `src/main/java/gov/brgy/ebms/usermgmt/dto/CreateUserRequest.java`:

```java
package gov.brgy.ebms.usermgmt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateUserRequest(
    @NotBlank @Size(max = 64) String username,
    @Size(max = 128) String email,
    @NotBlank @Size(max = 160) String fullName,
    @NotBlank @Size(min = 10, max = 128) String password,
    @NotNull List<Long> roleIds
) {}
```

Create `src/main/java/gov/brgy/ebms/usermgmt/dto/UpdateUserRequest.java`:

```java
package gov.brgy.ebms.usermgmt.dto;

import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
    @Size(max = 160) String fullName,
    @Size(max = 128) String email,
    Boolean enabled
) {}
```

Create `src/main/java/gov/brgy/ebms/usermgmt/dto/AssignRolesRequest.java`:

```java
package gov.brgy.ebms.usermgmt.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AssignRolesRequest(@NotNull List<Long> roleIds) {}
```

Create `src/main/java/gov/brgy/ebms/usermgmt/dto/UserDetailResponse.java`:

```java
package gov.brgy.ebms.usermgmt.dto;

import gov.brgy.ebms.security.entity.Role;
import gov.brgy.ebms.security.entity.User;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

public record UserDetailResponse(
    Long id,
    String username,
    String email,
    String fullName,
    Boolean enabled,
    Set<String> roles,
    int failedLoginAttempts,
    LocalDateTime lockedUntil,
    boolean forcedPasswordChange,
    LocalDateTime createdAt
) {
    public static UserDetailResponse from(User u) {
        return new UserDetailResponse(
            u.getId(), u.getUsername(), u.getEmail(), u.getFullName(),
            u.getEnabled(),
            u.getRoles().stream().map(Role::getCode).collect(Collectors.toSet()),
            u.getFailedLoginAttempts(), u.getLockedUntil(),
            u.isForcedPasswordChange(), u.getCreatedAt()
        );
    }
}
```

- [ ] **Step 2: Check that `User` has the required getters/setters**

Open `src/main/java/gov/brgy/ebms/security/entity/User.java`. Verify these methods exist (add them following the existing getter/setter pattern if any are missing):
- `getEnabled()` / `setEnabled(Boolean)`
- `getRoles()` / `setRoles(Set<Role>)`
- `isForcedPasswordChange()` / `setForcedPasswordChange(boolean)`
- `getFailedLoginAttempts()` / `setFailedLoginAttempts(int)`
- `getLockedUntil()` / `setLockedUntil(LocalDateTime)`
- `getCreatedAt()`

- [ ] **Step 3: Write the failing tests**

Create `src/test/java/gov/brgy/ebms/usermgmt/UserManagementServiceTest.java`:

```java
package gov.brgy.ebms.usermgmt;

import gov.brgy.ebms.security.entity.Role;
import gov.brgy.ebms.security.entity.User;
import gov.brgy.ebms.security.repository.RoleRepository;
import gov.brgy.ebms.security.repository.UserRepository;
import gov.brgy.ebms.usermgmt.dto.AssignRolesRequest;
import gov.brgy.ebms.usermgmt.dto.CreateUserRequest;
import gov.brgy.ebms.usermgmt.dto.UpdateUserRequest;
import gov.brgy.ebms.usermgmt.dto.UserDetailResponse;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks UserManagementService userManagementService;

    @Test
    void createUser_setsEncodedPasswordAndForcedChange() {
        CreateUserRequest req = new CreateUserRequest(
            "jdelacruz", "j@test.com", "Juan Dela Cruz", "Password123", List.of(1L));
        Role staffRole = buildRole(1L, "STAFF");
        when(roleRepository.findAllById(List.of(1L))).thenReturn(List.of(staffRole));
        when(passwordEncoder.encode("Password123")).thenReturn("$2a$hashed");

        User saved = buildUser(1L, "jdelacruz");
        saved.setForcedPasswordChange(true);
        saved.setPasswordHash("$2a$hashed");
        saved.setRoles(new HashSet<>(Set.of(staffRole)));
        when(userRepository.save(any())).thenReturn(saved);

        UserDetailResponse result = userManagementService.createUser(req);

        assertThat(result.username()).isEqualTo("jdelacruz");
        assertThat(result.forcedPasswordChange()).isTrue();
        assertThat(result.roles()).contains("STAFF");
        verify(passwordEncoder).encode("Password123");
    }

    @Test
    void updateUser_disablingAccount_updatesEnabledFlag() {
        User existing = buildUser(1L, "jdelacruz");
        existing.setEnabled(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserDetailResponse result = userManagementService.updateUser(1L, new UpdateUserRequest(null, null, false));

        assertThat(result.enabled()).isFalse();
    }

    @Test
    void assignRoles_replacesExistingRoles() {
        User existing = buildUser(1L, "jdelacruz");
        existing.setRoles(new HashSet<>(Set.of(buildRole(1L, "STAFF"))));
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        Role secretary = buildRole(2L, "SECRETARY");
        when(roleRepository.findAllById(List.of(2L))).thenReturn(List.of(secretary));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserDetailResponse result = userManagementService.assignRoles(1L, new AssignRolesRequest(List.of(2L)));

        assertThat(result.roles()).containsExactly("SECRETARY");
        assertThat(result.roles()).doesNotContain("STAFF");
    }

    @Test
    void unlockUser_clearsLockoutFields() {
        User existing = buildUser(1L, "jdelacruz");
        existing.setFailedLoginAttempts(5);
        existing.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserDetailResponse result = userManagementService.unlockUser(1L);

        assertThat(result.failedLoginAttempts()).isZero();
        assertThat(result.lockedUntil()).isNull();
    }

    @Test
    void getUser_notFound_throwsEntityNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userManagementService.getUser(99L))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("99");
    }

    private User buildUser(Long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setFullName("Test User");
        u.setPasswordHash("$2a$hashed");
        u.setEnabled(true);
        u.setRoles(new HashSet<>());
        return u;
    }

    private Role buildRole(Long id, String code) {
        Role r = new Role();
        r.setId(id);
        r.setCode(code);
        r.setNameEn(code);
        r.setNameFil(code);
        return r;
    }
}
```

- [ ] **Step 4: Run to verify they fail**

```
mvn test -pl ebms-server -Dtest=UserManagementServiceTest -q
```

Expected: `BUILD FAILURE` — `UserManagementService` does not exist yet.

- [ ] **Step 5: Check if `RoleRepository` exists; create it if missing**

```
Get-ChildItem -Recurse -Filter "RoleRepository.java" "C:\Users\USER\Documents\GitHub\eBMS\ebms-server\src"
```

If not found, create `src/main/java/gov/brgy/ebms/security/repository/RoleRepository.java`:

```java
package gov.brgy.ebms.security.repository;

import gov.brgy.ebms.security.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {}
```

- [ ] **Step 6: Write the service**

Create `src/main/java/gov/brgy/ebms/usermgmt/UserManagementService.java`:

```java
package gov.brgy.ebms.usermgmt;

import gov.brgy.ebms.security.entity.Role;
import gov.brgy.ebms.security.entity.User;
import gov.brgy.ebms.security.repository.RoleRepository;
import gov.brgy.ebms.security.repository.UserRepository;
import gov.brgy.ebms.usermgmt.dto.AssignRolesRequest;
import gov.brgy.ebms.usermgmt.dto.CreateUserRequest;
import gov.brgy.ebms.usermgmt.dto.UpdateUserRequest;
import gov.brgy.ebms.usermgmt.dto.UserDetailResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Service
public class UserManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserManagementService(
        UserRepository userRepository,
        RoleRepository roleRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN')")
    @Transactional(readOnly = true)
    public Page<UserDetailResponse> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserDetailResponse::from);
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN')")
    @Transactional
    public UserDetailResponse createUser(CreateUserRequest request) {
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setForcedPasswordChange(true);

        List<Role> roles = roleRepository.findAllById(request.roleIds());
        user.setRoles(new HashSet<>(roles));

        return UserDetailResponse.from(userRepository.save(user));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN')")
    @Transactional(readOnly = true)
    public UserDetailResponse getUser(Long id) {
        return UserDetailResponse.from(findById(id));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN')")
    @Transactional
    public UserDetailResponse updateUser(Long id, UpdateUserRequest request) {
        User user = findById(id);
        if (request.fullName() != null) user.setFullName(request.fullName());
        if (request.email() != null) user.setEmail(request.email());
        if (request.enabled() != null) user.setEnabled(request.enabled());
        return UserDetailResponse.from(userRepository.save(user));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN')")
    @Transactional
    public UserDetailResponse assignRoles(Long id, AssignRolesRequest request) {
        User user = findById(id);
        List<Role> roles = roleRepository.findAllById(request.roleIds());
        user.setRoles(new HashSet<>(roles));
        return UserDetailResponse.from(userRepository.save(user));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BARANGAY_CAPTAIN')")
    @Transactional
    public UserDetailResponse unlockUser(Long id) {
        User user = findById(id);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        return UserDetailResponse.from(userRepository.save(user));
    }

    private User findById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
    }
}
```

- [ ] **Step 7: Run to verify they pass**

```
mvn test -pl ebms-server -Dtest=UserManagementServiceTest -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```
git add src/main/java/gov/brgy/ebms/usermgmt/ \
        src/test/java/gov/brgy/ebms/usermgmt/
git commit -m "feat: add UserManagementService with create/update/assign-roles/unlock"
```

---

### Task 8: UserManagementController

**Files:**
- Create: `src/main/java/gov/brgy/ebms/api/UserManagementController.java`

**Interfaces:**
- Consumes: `UserManagementService` (Task 7)
- Produces: REST endpoints at `/api/v1/users`

- [ ] **Step 1: Write the controller**

Create `src/main/java/gov/brgy/ebms/api/UserManagementController.java`:

```java
package gov.brgy.ebms.api;

import gov.brgy.ebms.usermgmt.UserManagementService;
import gov.brgy.ebms.usermgmt.dto.AssignRolesRequest;
import gov.brgy.ebms.usermgmt.dto.CreateUserRequest;
import gov.brgy.ebms.usermgmt.dto.UpdateUserRequest;
import gov.brgy.ebms.usermgmt.dto.UserDetailResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserManagementController {

    private final UserManagementService userManagementService;

    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    public ResponseEntity<Page<UserDetailResponse>> list(Pageable pageable) {
        return ResponseEntity.ok(userManagementService.listUsers(pageable));
    }

    @PostMapping
    public ResponseEntity<UserDetailResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(userManagementService.createUser(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDetailResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(userManagementService.getUser(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDetailResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateUserRequest request
    ) {
        return ResponseEntity.ok(userManagementService.updateUser(id, request));
    }

    @PatchMapping("/{id}/roles")
    public ResponseEntity<UserDetailResponse> assignRoles(
        @PathVariable Long id,
        @Valid @RequestBody AssignRolesRequest request
    ) {
        return ResponseEntity.ok(userManagementService.assignRoles(id, request));
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<UserDetailResponse> unlock(@PathVariable Long id) {
        return ResponseEntity.ok(userManagementService.unlockUser(id));
    }
}
```

- [ ] **Step 2: Run the full test suite**

```
mvn test -pl ebms-server -q
```

Expected: `BUILD SUCCESS` — all tests pass.

- [ ] **Step 3: Commit**

```
git add src/main/java/gov/brgy/ebms/api/UserManagementController.java
git commit -m "feat: add UserManagementController for user/role admin operations"
```

---

## End-to-End Verification

After all tasks are complete:

1. **Start the app** — run from NetBeans or `mvn spring-boot:run -pl ebms-server`. App must start without Flyway errors. If V5 fails, check the SQL in `V5__add_certificates_table.sql`.

2. **Error envelope** — `POST /api/v1/certificates` with empty JSON body `{}`:
   - Expected: `{ "success": false, "message": "Validation failed", "errors": ["residentId: must not be null", "purpose: must not be blank"], "timestamp": "...", "path": "/api/v1/certificates" }`

3. **Certificate happy path** (log in as SECRETARY via `POST /api/v1/auth/login`):
   - `POST /api/v1/certificates` → 201, `"status": "REQUESTED"`
   - `POST /api/v1/certificates/{id}/approve` → 200, `"status": "APPROVED"`
   - `GET /api/v1/certificates/{id}/document` → PDF file downloads

4. **IDOR check** — log in as RESIDENT; `POST /api/v1/certificates` with a `residentId` not linked to your account → 403.

5. **User management** (log in as BARANGAY_CAPTAIN):
   - `POST /api/v1/users` → 201, `"forcedPasswordChange": true`
   - `POST /api/v1/auth/login` with new credentials → `"requiresPasswordChange": true`
   - `PATCH /api/v1/auth/change-password` → next login works normally

6. **Account unlock** — trigger 5 failed logins; `POST /api/v1/users/{id}/unlock` as BARANGAY_CAPTAIN → next login succeeds.
