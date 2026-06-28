# Known Issues and Technical Debt

All items flagged during the v1 security and QA review pipeline are listed here.
Items marked **FIXED** were resolved before final release. Open items are scheduled for the next sprint.

---

## Security Findings

### SEC-1: `ClearanceService.listByResident()` — Service-Layer Ownership Check
**Severity:** Medium (originally) → **FIXED in final release**
`enforceResidentOwnership()` added at the start of `listByResident()` (`ClearanceService.java:106`).
RESIDENT callers can only list their own clearances.

---

### SEC-2: `SecurityUtils.getAuthenticatedUserId()` — Portal-Session Fallback
**Severity:** Medium (originally) → **FIXED in final release**
Session fallback added (`SecurityUtils.java:44-56`). `PortalAuthSuccessHandler` stores the DB user ID in the HTTP session at portal login (`PortalAuthSuccessHandler.java:42-43`). `enforceResidentOwnership()` now throws `AccessDeniedException` instead of silently returning when caller is RESIDENT and ID is unresolvable.

---

### SEC-3: `AuditLogRepository` Inherits JPA Delete Methods
**Severity:** Medium (originally) → **Partially mitigated**

**What was done:** `AuditLog` entity now carries `@org.hibernate.annotations.Immutable` and all `@Column` annotations have `updatable = false`. Database-level REVOKE in `docs/setup/create-db.sql` prevents the underlying SQL from executing.

**What remains:** `AuditLogRepository` still extends `JpaRepository<AuditLog, Long>`, which exposes inherited `deleteById()`, `delete()`, `deleteAll()` methods at the application code level. They fail at runtime (DB rejects them) but are not blocked at compile time.

**Planned fix (next sprint):** Replace `extends JpaRepository` with a narrower `extends Repository` interface that declares only `save()` and read methods.

---

### SEC-4: `AuditLog` Entity `@Immutable` Annotation
**Severity:** Medium (originally) → **FIXED in final release**
`@org.hibernate.annotations.Immutable` added to `AuditLog` entity (`AuditLog.java:8`). All `@Column` declarations now carry `updatable = false`. Hibernate will not issue UPDATE statements on managed `AuditLog` instances.

---

### SEC-5: PDF File Path Not Confined to Storage Root
**Severity:** Low — identified in final security review
**Location:** `ClearanceController.java:77-78`

**Description:** `downloadDocument()` serves the file path stored in `clearance_documents.file_path` without verifying the resolved path starts within the configured `${document.storage.path}` root. The path is server-generated (never from user input) and not exploitable without a compromised database account. Defense-in-depth gap only.

**Planned fix (next sprint):**
```java
Path resolved = Paths.get(doc.getFilePath()).normalize();
Path storageRoot = Paths.get(storagePath).normalize();
if (!resolved.startsWith(storageRoot)) {
    throw new ResponseStatusException(HttpStatus.NOT_FOUND);
}
```

---

## Architectural Notes

### ARCH-1: REJECT and Complaint Status-Change Audit Entries Have No Before-State
**Location:** `AuditAspect` — `@Auditable` on `ClearanceService.reject()`, `ComplaintService.updateStatus()`

`approve()` correctly captures before-state (entity fetched from DB before mutation). `reject()` does not specify `entityClass` in its `@Auditable` annotation, so `before_json` is null for rejection events. For complaint status changes, `complaint_status_history` provides the transition record; no entity snapshot is written to `audit_log.before_json`.

**Planned fix:** Align `@Auditable` parameters on `reject()` to match `approve()`. Decide whether complaint status changes should also write entity snapshots.

---

### ARCH-2: JavaFX Desktop Module is a v1 Scaffold
**Location:** `ebms-desktop` module

The desktop module provides a login screen and dashboard shell only. It connects to `http://localhost:8080` by default (plain HTTP), inconsistent with the server's HTTPS-first configuration.

**Do not distribute to end users in current state.** All staff operations should use the REST API or the Thymeleaf portal in v1.

**Planned for v2:** Full admin UI for all modules, HTTPS support with truststore for the dev certificate.

---

### ARCH-3: `PUT /households/{id}/head` Does Not Sync `household_members` Table
**Location:** `HouseholdService.setHouseholdHead()` — identified in final QA review

Setting a new household head updates `households.head_resident_id` but does not insert or update the corresponding `household_members` row to reflect the `HEAD` relationship. If the new head is not already in `household_members`, the member table becomes inconsistent with the `households` table.

**Planned fix:** `setHouseholdHead()` should upsert a `household_members` row with `relationship = 'HEAD'` and demote the previous head's member row to `'MEMBER'`.
