# Changelog

All notable changes to e-Barangay Management System are documented here.

---

## [1.0.0-SNAPSHOT] — 2026-06-27

Initial release.

### Added

#### Infrastructure
- Maven multi-module project: `ebms-server` (Spring Boot 3.3.5) and `ebms-desktop` (JavaFX 21)
- Java 21 source and target; runs on JDK 25
- MySQL 8 production database with Flyway schema management (V1–V4 migrations)
- H2 in-memory database for automated tests
- HTTPS by default via embedded Tomcat with PKCS12 keystore support
- BCrypt (strength 12) password hashing
- Bilingual support: English (`en`) and Filipino (`fil`) via Spring Messages; user-selectable in portal (`?lang=fil`) and API (`Accept-Language` header)
- `GlobalExceptionHandler` — consistent JSON error responses for 400, 401, 403, 404, 409, 500
- `JacksonConfig` — Java time module for ISO 8601 date serialisation

#### Database Schema (V1)
- 16 tables: `roles`, `users`, `user_roles`, `refresh_tokens`, `login_attempts`, `residents`, `households`, `household_members`, `clearance_requests`, `clearance_documents`, `complaints`, `complaint_parties`, `complaint_status_history`, `fees`, `audit_log`, `document_sequences`
- Soft-delete pattern on `users`, `residents`, `households`, `clearance_requests`, `complaints`, `fees` via `@SQLRestriction("deleted_at IS NULL")`
- Role seed data: SUPER_ADMIN, BARANGAY_CAPTAIN, SECRETARY, STAFF, RESIDENT
- Default `admin` account (BCrypt-hashed, forced password change on first login)

#### Auth Module
- JWT access tokens (HMAC-SHA256, 15-minute expiry) via JJWT 0.12.6
- Refresh token rotation (7-day expiry, SHA-256 hashed at rest)
- Account lockout: 5 failed attempts triggers a 15-minute lock (both thresholds configurable)
- Constant-time dummy hash comparison on unknown usernames to prevent timing-based username enumeration
- `POST /api/v1/auth/login` — returns `requiresPasswordChange` flag for forced-change accounts
- `POST /api/v1/auth/refresh` — token rotation, old token immediately revoked
- `POST /api/v1/auth/logout` — revokes all refresh tokens for the authenticated user
- V3 migration: `forced_password_change` column on `users`; admin account flagged on install
- V4 migration: `UNIQUE` constraint on `users.username`

#### Residents Module
- `GET /api/v1/residents` — paginated search with optional `q` parameter
- `GET /api/v1/residents/{id}` — single record; RESIDENT role limited to own record
- `POST /api/v1/residents` — create with duplicate detection (same first name + last name + birthdate, case-insensitive); `confirmDuplicate=true` bypasses the 409 response
- `PUT /api/v1/residents/{id}` — full update with duplicate re-check
- `DELETE /api/v1/residents/{id}` — soft delete (SUPER_ADMIN / BARANGAY_CAPTAIN only)
- Auto-generated resident codes: `RES-YYYY-NNNNNN`
- Audit entries generated via `@Auditable` AOP for CREATE, UPDATE, DELETE

#### Households Module
- `GET /api/v1/households` — paginated list
- `GET /api/v1/households/{id}` — single record
- `POST /api/v1/households` — create; enforces single-head invariant (one resident can head only one household); automatically creates a `household_members` entry for the head
- `PUT /api/v1/households/{id}` — update address and/or head
- `DELETE /api/v1/households/{id}` — soft delete
- Auto-generated household codes: `HH-YYYY-NNNNNN`

#### Clearances Module
- `POST /api/v1/clearances` — submit a request; RESIDENT callers restricted to own record; warns (non-blocking) if a pending clearance exists
- `GET /api/v1/clearances` — paginated list filterable by status
- `GET /api/v1/clearances/{id}` — single record; RESIDENT callers restricted to own records
- `POST /api/v1/clearances/{id}/review` — transitions SUBMITTED → UNDER_REVIEW
- `POST /api/v1/clearances/{id}/approve` — transitions UNDER_REVIEW → APPROVED; prevents self-approval; warns (non-blocking) if resident has unresolved complaints; triggers PDF generation
- `POST /api/v1/clearances/{id}/reject` — rejects any non-APPROVED clearance
- PDF generation via OpenPDF 2.0.3; SHA-256 checksum stored in `clearance_documents`
- Auto-generated control numbers: `{prefix}-CLR-YYYY-NNNNNN`
- Audit entries for CREATE, APPROVE, REJECT

#### Complaints Module
- `POST /api/v1/complaints` — file a blotter entry with one or more parties (COMPLAINANT, RESPONDENT, WITNESS)
- `GET /api/v1/complaints` — paginated list filterable by status
- `GET /api/v1/complaints/{id}` — single record
- `POST /api/v1/complaints/{id}/transition?newStatus=X&note=Y` — advance the complaint lifecycle; valid transitions: FILED→UNDER_MEDIATION or ESCALATED, UNDER_MEDIATION→RESOLVED or ESCALATED; terminal states: RESOLVED, ESCALATED
- `DELETE /api/v1/complaints/{id}` — soft delete
- `complaint_status_history` record created on every transition
- Auto-generated case numbers: `{prefix}-BLT-YYYY-NNNNNN`
- Audit entries for CREATE and STATUS_CHANGE

#### Fees Module
- `POST /api/v1/fees` — create a fee record (linked optionally to a clearance)
- `GET /api/v1/fees/unpaid` — list all UNPAID fees (non-paginated)
- `GET /api/v1/fees/{id}` — single fee record
- `POST /api/v1/fees/{id}/pay` — mark as PAID; assigns official receipt number (`{prefix}-OR-YYYY-NNNNNN`) and records `paid_at` and `collected_by`
- `POST /api/v1/fees/{id}/waive` — mark as WAIVED (SUPER_ADMIN / BARANGAY_CAPTAIN only)
- Audit entry on payment

#### Audit Module
- `AuditService` — insert-only audit log with SHA-256 hash chain linking every row to the previous
- `@Auditable` AOP annotation — intercepts annotated service methods; captures before-state for UPDATE/DELETE; records entity type, action, actor, IP address, before/after JSON
- `StartupGrantVerifier` — on `ApplicationReadyEvent`, confirms the app DB user cannot UPDATE `audit_log`; logs a WARNING if the V2 DBA grant has not been applied
- V2 migration documents the DBA steps required to restrict `audit_log` to INSERT + SELECT

#### Document Numbering
- `SequenceService` — year-scoped, per-type sequences stored in `document_sequences`; incremented inside `REQUIRES_NEW` transactions with pessimistic locking (`SELECT ... FOR UPDATE`)
- Configurable prefix via `barangay.doc.prefix` (default `BRGY`)

#### Resident Portal (Thymeleaf)
- `/portal/login` — form login page
- `/portal/dashboard` — post-login landing page
- `/portal/resident/profile` — authenticated resident's own profile view
- `/portal/resident/clearances` — resident's own clearance history and request submission
- Session-based auth; `HttpOnly`, `Secure`, `SameSite=Lax` cookies; 30-minute session timeout; session fixation protection via `changeSessionId()`
- Language switcher at `/portal/locale`

#### Desktop Module (Scaffold)
- JavaFX 21 application (`ebms-desktop`)
- Login screen with username/password fields backed by `ApiClient` → `POST /api/v1/auth/login`
- Dashboard shell displayed after successful authentication
- Full admin UI deferred to v2

### Known Limitations

See [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md) for the four Medium security findings and two architectural notes identified during the v1 review.

Several API endpoints specified in the v1 design are not yet implemented in REST controllers, including `/auth/me`, `/auth/change-password`, resident restore, household member sub-resources, clearance document download, complaint unresolved list, audit log query endpoints, and public config endpoints. See [docs/API.md — Endpoints Specified but Not Yet Implemented](docs/API.md#endpoints-specified-but-not-yet-implemented).
