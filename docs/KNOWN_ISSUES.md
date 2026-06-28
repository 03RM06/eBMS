# Known Issues and Technical Debt

All items flagged during the v1 security and QA review pipeline are listed here.
Items marked **FIXED** were resolved before final release. Open items are scheduled for the next sprint.

---

## Security Findings

### SEC-1: `ClearanceService.listByResident()` — Service-Layer Ownership Check
**Severity:** Medium (originally) → **FIXED in v1 release**
`enforceResidentOwnership()` added at the start of `listByResident()` (`ClearanceService.java:106`).
RESIDENT callers can only list their own clearances.

---

### SEC-2: `SecurityUtils.getAuthenticatedUserId()` — Portal-Session Fallback
**Severity:** Medium (originally) → **FIXED in v1 release**
Session fallback added (`SecurityUtils.java:44-56`). `PortalAuthSuccessHandler` stores the DB user ID in the HTTP session at portal login (`PortalAuthSuccessHandler.java:42-43`). `enforceResidentOwnership()` now throws `AccessDeniedException` instead of silently returning when caller is RESIDENT and ID is unresolvable.

---

### SEC-3: `AuditLogRepository` Inherits JPA Delete Methods
**Severity:** Medium (originally) → **Partially mitigated**

**What was done:** `AuditLog` entity carries `@org.hibernate.annotations.Immutable` and all `@Column` annotations have `updatable = false`. Database-level REVOKE in `docs/setup/create-db.sql` prevents the underlying SQL from executing.

**What remains:** `AuditLogRepository` still extends `JpaRepository<AuditLog, Long>`, exposing inherited `deleteById()`, `delete()`, `deleteAll()` at the application code level. They fail at runtime (DB rejects them) but are not blocked at compile time. Cannot change to a narrower `Repository` without breaking `AuditLogImmutabilityTest.auditLogRepository_extendsJpaRepository()` — that structural test was written knowing this compromise.

**Planned fix (next sprint):** Update the test, then replace `extends JpaRepository` with a narrower interface declaring only `save()` and read methods.

---

### SEC-4: `AuditLog` Entity `@Immutable` Annotation
**Severity:** Medium (originally) → **FIXED in v1 release**
`@org.hibernate.annotations.Immutable` added to `AuditLog` entity. All `@Column` declarations carry `updatable = false`.

---

### SEC-5: PDF File Path Confinement
**Severity:** Low (originally) → **FIXED in sprint 2**
`ClearanceController.downloadDocument()` now performs a two-stage check:
1. Lexical normalization + `startsWith(storageRoot)` — blocks `../..` traversal before any filesystem I/O.
2. `toRealPath()` re-check after `file.exists()` — prevents symlink escape from inside the storage directory.

Test coverage: `ClearancePathConfinementTest` (3 tests: traversal rejection, missing file, valid file).

---

## Architectural Notes

### ARCH-1: Before-State Capture for REJECT and STATUS_CHANGE Actions
**Location:** `AuditAspect.java` — originally gated on `"UPDATE"` or `"DELETE"` action names
**Status:** **FIXED in sprint 2**

`AuditAspect` guard changed to `auditable.entityClass() != Void.class`. Any `@Auditable` method that declares a non-Void entity class now captures before-state regardless of action name. Covers `ClearanceService.reject()` (action `"REJECT"`) and `ComplaintService.transition()` (action `"STATUS_CHANGE"`). Tests: `AuditAspectBeforeStateTest` — 2 new tests added.

---

### ARCH-2: JavaFX Desktop Module — Full Admin UI (v2)
**Location:** `ebms-desktop` module
**Status:** **FIXED in sprint 4**

The desktop module now provides a complete admin UI for all seven server modules:
Residents, Households, Clearances, Complaints, Fees, Audit Log, and Authentication.

Key implementation details:
- Connects to `https://localhost:8443` (HTTPS) by default; configurable via `-Debms.baseUrl` system property or `EBMS_BASE_URL` environment variable.
- Dev self-signed certificate accepted via a trust-all `X509TrustManager` (isolated in `buildDevSslContext()`, gated by `Config.TRUST_ALL_CERTS`). Set `Config.TRUST_ALL_CERTS = false` and import the real certificate for production deployment.
- JWT access/refresh tokens stored in memory only (never written to disk). Automatic single-retry on 401 with thread-safe refresh.
- Runtime EN/Filipino language switching via observable `I18n` locale (persists to server via `PUT /auth/me/locale`).
- Role-based UI gating: Audit Log visible to CAPTAIN+ only; Verify button visible to SUPER_ADMIN only; delete/waive/restore restricted by role.
- Clearance PDF download opens the document in the OS default PDF viewer via `java.awt.Desktop`.

Known limitation (Security SEC-04): Clearance PDF temp files are not deleted after the viewer closes — add `tmp.toFile().deleteOnExit()` before production use.

---

### ARCH-3: `PUT /households/{id}/head` Head/Member Sync
**Location:** `HouseholdService.setHouseholdHead()`
**Status:** **FIXED in sprint 2**

`setHouseholdHead()` now:
- Demotes the previous head's `household_members` row from `"HEAD"` to `"MEMBER"` when switching to a different resident.
- Upserts the new head: updates the existing member row if present, or inserts a new `"HEAD"` row.
- Guards against `null` residentId (throws `IllegalArgumentException` immediately).
- Guards against a resident heading two households simultaneously (`existsByHeadResidentId` check, matching `create()` and `update()` behavior).

Tests: `HouseholdServiceTest` — 3 new tests added.

---

### ARCH-4: Cross-Household HEAD Check in `setHouseholdHead` — No Dedicated Test
**Status:** Open — non-blocking

The cross-household HEAD guard added in sprint 2 (`existsByHeadResidentId` check in `setHouseholdHead`) is not covered by a dedicated test. The equivalent behavior is tested in `create` and `update` paths. `HouseholdServiceTest` should add a case: call `setHouseholdHead()` with a residentId that already heads another household and assert `IllegalArgumentException`.

---

### ARCH-5: Audit Sentinel Not Distinguishable from EntityManager Failure
**Status:** Open — low priority

When `EntityManager` is null (unit tests) or the entity has already been deleted before the aspect fires, `AuditAspect` stores a sentinel `Map.of("entityType", ..., "note", "before_state_unavailable")` instead of the real before-state. This sentinel is not distinguishable in the audit log from a legitimate EntityManager lookup failure in production.

**Planned fix (next sprint):** Add a `"sentinel": true` or `"reason": "entity_not_found"` field to the sentinel so operators can identify audit entries with unavailable before-state in production audits.
