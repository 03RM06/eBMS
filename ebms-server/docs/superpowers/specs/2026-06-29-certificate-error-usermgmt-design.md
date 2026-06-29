# eBMS: Certificate Module + Error Envelope + User Management

## Context

eBMS (`C:\Users\USER\Documents\GitHub\eBMS\ebms-server`) is a Spring Boot 3.3.5 barangay management system with 43 REST endpoints, JWT auth, audit logging, and OpenPDF 2.0.3 already in the dependency tree. Three improvements were requested:

1. **Certificate/document generation** — four new barangay certificate types (Indigency, Residency, Business Permit Endorsement, Good Moral Character) following a simpler workflow than the existing clearance module.
2. **Consistent error envelope** — all 43 endpoints currently return errors in different formats; a single `@ControllerAdvice` fixes this without touching any existing controller.
3. **User + role management API** — admins currently create users via DB scripts; a new `/api/v1/users` surface exposes this via the API.

The three areas are independent and can be implemented and shipped in any order.

---

## Step 0 — Write design doc

Before any code, create and commit:
```
ebms-server/docs/superpowers/specs/2026-06-29-certificate-error-usermgmt-design.md
```
Content: copy of this plan. Commit message: `docs: add design spec for certificate module, error envelope, user management`.

---

## Step 1 — Global error envelope (lowest risk, ship first)

**File to create:** `src/main/java/gov/brgy/ebms/config/GlobalExceptionHandler.java`

Annotate with `@RestControllerAdvice`. Return a record:
```java
record ApiErrorResponse(boolean success, String message, List<String> errors, Instant timestamp, String path) {}
```

Map these exceptions (all return `success: false`, no stack trace):

| Exception | HTTP Status |
|-----------|-------------|
| `MethodArgumentNotValidException` | 400 — collect `field: message` pairs into `errors[]` |
| `HttpMessageNotReadableException` | 400 — "Malformed request body" |
| `EntityNotFoundException` / `NoSuchElementException` | 404 |
| `AccessDeniedException` | 403 |
| `AuthenticationException` | 401 |
| `DataIntegrityViolationException` | 409 — "Conflict: duplicate or constraint violation" |
| `IllegalStateException` | 409 — use exception message |
| `Exception` (catch-all) | 500 — "An unexpected error occurred" (never leak message) |

Inject `HttpServletRequest` to populate `path`.

**Existing security handlers** (`SecurityConfig` already returns JSON errors for auth failures) — do not replace those. The `@ControllerAdvice` only handles exceptions that reach the dispatcher servlet.

**Test:** `GlobalExceptionHandlerTest` — use `@WebMvcTest` with a stub controller that deliberately throws each exception type; assert response shape and status.

---

## Step 2 — Certificate module

### 2a — Entity & repository

**File:** `src/main/java/gov/brgy/ebms/certificate/Certificate.java`

Key fields:
```
id (Long, auto)
controlNumber (String, unique, not null)
residentId (Long, FK)
certificateType (CertificateType enum)
purpose (String, not null)
status (CertificateStatus enum, default REQUESTED)
remarks (String, nullable — populated on rejection)
approvedBy (Long, nullable — userId)
approvedAt (Instant, nullable)
filePath (String, nullable)
sha256Checksum (String, nullable)
createdAt, updatedAt (auto)
deletedAt (soft-delete, @SQLRestriction("deleted_at IS NULL"))
```

Annotate with `@Auditable` (existing aspect picks it up automatically).

**File:** `src/main/java/gov/brgy/ebms/certificate/CertificateType.java`
```java
enum CertificateType { INDIGENCY, RESIDENCY, BUSINESS_PERMIT_ENDORSEMENT, GOOD_MORAL }
```

**File:** `src/main/java/gov/brgy/ebms/certificate/CertificateStatus.java`
```java
enum CertificateStatus { REQUESTED, APPROVED, REJECTED }
```

**File:** `src/main/java/gov/brgy/ebms/certificate/CertificateRepository.java`
- Extend `JpaRepository<Certificate, Long>`
- `Page<Certificate> findByStatus(CertificateStatus, Pageable)`
- `Page<Certificate> findByCertificateType(CertificateType, Pageable)`
- `Page<Certificate> findByStatusAndCertificateType(CertificateStatus, CertificateType, Pageable)`

### 2b — SequenceService extension

**File to edit:** `src/main/java/gov/brgy/ebms/numbering/SequenceService.java`

Add one method: `nextCertificateNumber()` — same pattern as `nextClearanceNumber()`, format `CERT-{year}-{5-digit-seq}`. Add corresponding `DocumentSequence` seed row in a new Flyway migration.

### 2c — PDF generator

**File:** `src/main/java/gov/brgy/ebms/certificate/CertificatePdfGenerator.java`

Mirrors `clearance/ClearancePdfGenerator`. Method: `File generateFor(Certificate cert, Resident resident)`.
- Uses OpenPDF (already on classpath)
- Writes to `${document.storage.path}/certificates/{controlNumber}.pdf`
- Returns `File`; caller computes SHA-256 and stores checksum
- Each `CertificateType` gets its own boilerplate text block — the exact Filipino/English wording for each certificate type must be confirmed with the user or sourced from an official barangay template before implementation

### 2d — Service

**File:** `src/main/java/gov/brgy/ebms/certificate/CertificateService.java`

```
submit(CertificateRequest, String createdBy) → CertificateResponse
  - Validate residentId exists (ResidentRepository.findById)
  - RESIDENT role: reject if residentId ≠ caller's residentId (IDOR check)
  - Generate controlNumber via SequenceService.nextCertificateNumber()
  - Save; AuditService.log(CREATE)

list(CertificateType type, CertificateStatus status, Pageable) → Page<CertificateResponse>

findById(Long id) → CertificateResponse
  - RESIDENT role: IDOR check

approve(Long id, String approvedBy) → CertificateResponse
  - Load; throw IllegalStateException if status ≠ REQUESTED
  - CertificatePdfGenerator.generateFor(cert, resident)
  - Compute SHA-256 on written file
  - Update: status=APPROVED, filePath, sha256Checksum, approvedBy, approvedAt
  - AuditService.log(UPDATE)

reject(Long id, String remarks, String rejectedBy) → CertificateResponse
  - Load; throw IllegalStateException if status ≠ REQUESTED
  - Update: status=REJECTED, remarks
  - AuditService.log(UPDATE)

getDocument(Long id) → Path
  - Load; throw IllegalStateException if status ≠ APPROVED
  - Resolve symlinks (path traversal protection, same as ClearanceController)
```

### 2e — Controller

**File:** `src/main/java/gov/brgy/ebms/certificate/CertificateController.java`

Base path: `@RequestMapping("/api/v1/certificates")`

| Method | Path | @PreAuthorize | Body / Params |
|--------|------|---------------|---------------|
| POST | `/` | STAFF, SECRETARY, BARANGAY_CAPTAIN, SUPER_ADMIN, RESIDENT | `@RequestBody CertificateRequest` |
| GET | `/` | STAFF+ | `?type=&status=` + `Pageable` |
| GET | `/{id}` | STAFF+, RESIDENT | — |
| POST | `/{id}/approve` | SECRETARY+ | — |
| POST | `/{id}/reject` | SECRETARY+ | `?remarks=` |
| GET | `/{id}/document` | STAFF+, RESIDENT | — streams `FileSystemResource` |

### 2f — Flyway migration

New file: `src/main/resources/db/migration/V{next}__add_certificates_table.sql`
- Create `certificates` table with all columns above
- Insert `document_sequence` seed row for `CERTIFICATE` entity type

### 2g — Tests

- `CertificateServiceTest` — submit (happy path + IDOR rejection + invalid-state transition), approve, reject, list
- `CertificatePathConfinementTest` — symlink attack on document download (mirrors `ClearancePathConfinementTest`)
- `CertificatePdfGeneratorTest` — each of the 4 types generates a non-empty PDF file

---

## Step 3 — User management

### 3a — Service

**File:** `src/main/java/gov/brgy/ebms/usermgmt/UserManagementService.java`

Reuses existing `User`, `Role`, `UserRepository`, `RoleRepository` from `security/` package.

```
listUsers(Pageable) → Page<UserDetailResponse>

createUser(CreateUserRequest, String createdBy) → UserDetailResponse
  - Validate username uniqueness (throw DataIntegrityViolationException if taken)
  - BCrypt(12) encode temporary password
  - forcedPasswordChange = true
  - Assign roles from RoleRepository.findAllById(roleIds)
  - Save User; AuditService.log(CREATE)

getUser(Long id) → UserDetailResponse
updateUser(Long id, UpdateUserRequest, String updatedBy) → UserDetailResponse
  - Editable fields: fullName, email, enabled

assignRoles(Long id, AssignRolesRequest, String updatedBy) → UserDetailResponse
  - Replace role set entirely

unlockUser(Long id, String updatedBy) → UserDetailResponse
  - Clear failedLoginAttempts = 0, lockedUntil = null
```

### 3b — Controller

**File:** `src/main/java/gov/brgy/ebms/usermgmt/UserManagementController.java`

Base path: `@RequestMapping("/api/v1/users")`
All endpoints: `@PreAuthorize("hasAnyRole('BARANGAY_CAPTAIN','SUPER_ADMIN')")`

| Method | Path | Notes |
|--------|------|-------|
| GET | `/` | Paginated |
| POST | `/` | Body: `CreateUserRequest` |
| GET | `/{id}` | Single user |
| PUT | `/{id}` | Body: `UpdateUserRequest` |
| PATCH | `/{id}/roles` | Body: `AssignRolesRequest { roleIds: [1,2] }` |
| POST | `/{id}/unlock` | No body |

### 3c — DTOs

- `CreateUserRequest`: `username`, `email`, `fullName`, `password`, `roleIds`
- `UpdateUserRequest`: `fullName`, `email`, `enabled`
- `AssignRolesRequest`: `roleIds` (List<Long>)
- `UserDetailResponse`: `id`, `username`, `email`, `fullName`, `enabled`, `roles[]`, `failedLoginAttempts`, `lockedUntil`, `forcedPasswordChange`, `createdAt`

### 3d — Tests

- `UserManagementServiceTest` — create (happy path + duplicate username), update, assign roles, unlock

---

## Existing utilities to reuse

| Utility | Location | Used by |
|---------|----------|---------|
| `SequenceService` | `numbering/SequenceService.java` | Certificate control numbers |
| `AuditService` | `audit/AuditService.java` | All three modules |
| `@Auditable` | `audit/` | Certificate entity |
| `ClearancePdfGenerator` | `clearance/ClearancePdfGenerator.java` | Pattern reference for `CertificatePdfGenerator` |
| `UserRepository`, `RoleRepository` | `security/` | User management service |
| `ResidentRepository` | `resident/` | Certificate IDOR check |

---

## Verification

1. **Build:** `mvn clean verify` must pass with zero errors.
2. **Error envelope:** `POST /api/v1/certificates` with empty body → `{ "success": false, "message": "Validation failed", "errors": ["purpose: must not be blank"] }`.
3. **Certificate happy path:** Submit → GET (status REQUESTED) → Approve → GET `/{id}/document` downloads a PDF.
4. **IDOR check:** Authenticate as RESIDENT; submit certificate for a different `residentId` → 403.
5. **User management:** `POST /api/v1/users` → user exists; login with temp password → `requiresPasswordChange: true`; change password → subsequent login succeeds.
6. **Unlock:** Lock an account via 5 failed logins; `POST /api/v1/users/{id}/unlock` as BARANGAY_CAPTAIN; login succeeds.
7. **Tests:** `mvn test` — all 17 existing + new tests green; coverage ≥ 80% on new classes.
