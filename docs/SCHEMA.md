# Database Schema Reference

**Engine:** InnoDB, `utf8mb4` character set throughout.
**Managed by:** Flyway — all schema changes go through versioned migration scripts in `ebms-server/src/main/resources/db/migration/`.

---

## Flyway Migration History

| Script | Description |
|---|---|
| `V1__init.sql` | Full schema creation + seed data (roles, default admin user) |
| `V2__db_user_grants.sql` | Audit record — DBA must manually apply REVOKE on `audit_log` |
| `V3__forced_password_change.sql` | Adds `forced_password_change` column to `users`; sets flag on `admin` account |
| `V4__username_unique.sql` | Adds `UNIQUE` constraint `uq_users_username` on `users.username` |

---

## Soft-Delete Pattern

Most tables implement soft deletion. A record is "deleted" by setting `deleted_at` to the current timestamp and `deleted_by` to the acting user's ID. The record remains in the database.

All JPA entities that support soft delete carry `@SQLRestriction("deleted_at IS NULL")`, which means Hibernate automatically appends `AND deleted_at IS NULL` to every query. Soft-deleted records are invisible to the application without a custom query. The affected tables are: `users`, `residents`, `households`, `clearance_requests`, `complaints`, and `fees`.

---

## Document Numbering System

Auto-generated document numbers follow a `{prefix}-{YYYY}-{NNNNNN}` pattern. Sequences are stored per document type per calendar year in the `document_sequences` table and incremented inside a `REQUIRES_NEW` transaction (pessimistic lock via `SELECT ... FOR UPDATE`).

| Document Type | Format | Example |
|---|---|---|
| Resident code | `RES-{YYYY}-{NNNNNN}` | `RES-2026-000001` |
| Household code | `HH-{YYYY}-{NNNNNN}` | `HH-2026-000005` |
| Clearance control number | `{prefix}-CLR-{YYYY}-{NNNNNN}` | `BRGY-CLR-2026-000007` |
| Blotter / complaint case number | `{prefix}-BLT-{YYYY}-{NNNNNN}` | `BRGY-BLT-2026-000003` |
| Official receipt | `{prefix}-OR-{YYYY}-{NNNNNN}` | `BRGY-OR-2026-000002` |

`{prefix}` defaults to `BRGY` and is configurable via `barangay.doc.prefix` in `application.properties`.

---

## Audit Hash Chain

Each row in `audit_log` carries two hash fields that together form a hash chain:

- `prev_hash` — the `row_hash` of the immediately preceding audit log row (NULL for the first entry).
- `row_hash` — SHA-256 hash of: `entityType | entityId | action | actorUsername | beforeJson | afterJson | prevHash`

To verify integrity, re-compute `row_hash` for each row in ascending `id` order and confirm it matches the stored value, and that each row's `prev_hash` equals the previous row's `row_hash`. Any tampering breaks the chain.

The `StartupGrantVerifier` component runs at application startup to confirm that the DB user cannot execute `UPDATE` on `audit_log`. If it can (because the DBA has not yet applied V2 grants), a `SECURITY WARNING` is logged.

---

## Tables

### `roles`

Lookup table for user roles. Seeded by V1 migration; not modified at runtime.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | NOT NULL | Primary key |
| `code` | VARCHAR(32) | NOT NULL UNIQUE | `SUPER_ADMIN`, `BARANGAY_CAPTAIN`, `SECRETARY`, `STAFF`, `RESIDENT` |
| `name_en` | VARCHAR(64) | NOT NULL | Display name in English |
| `name_fil` | VARCHAR(64) | NOT NULL | Display name in Filipino |
| `created_at` | TIMESTAMP | NOT NULL | Defaults to `CURRENT_TIMESTAMP` |

---

### `users`

System user accounts. One user can hold multiple roles. Users can be optionally linked to a resident record.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | NOT NULL | Primary key |
| `username` | VARCHAR(64) | NOT NULL UNIQUE | V4 added DB-level uniqueness |
| `email` | VARCHAR(128) | NULL | |
| `password_hash` | VARCHAR(72) | NOT NULL | BCrypt strength 12 |
| `full_name` | VARCHAR(160) | NOT NULL | |
| `resident_id` | BIGINT | NULL | FK → `residents.id`; links a RESIDENT-role user to their record |
| `preferred_locale` | VARCHAR(8) | NOT NULL | `en` or `fil`; default `en` |
| `enabled` | BOOLEAN | NOT NULL | `TRUE` by default |
| `failed_login_attempts` | INT | NOT NULL | Reset to 0 on successful login |
| `locked_until` | TIMESTAMP | NULL | Set when failed attempts reach threshold |
| `last_login_at` | TIMESTAMP | NULL | Updated on successful login |
| `forced_password_change` | BOOLEAN | NOT NULL | Added by V3. Set `TRUE` for new accounts or after admin resets a password |
| `created_at` | TIMESTAMP | NOT NULL | |
| `updated_at` | TIMESTAMP | NOT NULL | Updated via `@PreUpdate` |
| `created_by` | BIGINT | NULL | Actor user ID |
| `updated_by` | BIGINT | NULL | Actor user ID |
| `deleted_at` | TIMESTAMP | NULL | Soft delete |
| `deleted_by` | BIGINT | NULL | Soft delete |

**Indexes:** `idx_users_deleted (deleted_at)`

---

### `user_roles`

Join table between `users` and `roles`. Composite PK prevents duplicate role assignments.

| Column | Type | Notes |
|---|---|---|
| `user_id` | BIGINT | FK → `users.id` |
| `role_id` | BIGINT | FK → `roles.id` |

---

### `refresh_tokens`

Stores hashed refresh tokens. The raw token is never persisted — only its SHA-256 hash.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | NOT NULL | Primary key |
| `user_id` | BIGINT | NOT NULL | FK → `users.id` |
| `token_hash` | CHAR(64) | NOT NULL UNIQUE | SHA-256 of the raw refresh token |
| `issued_at` | TIMESTAMP | NOT NULL | |
| `expires_at` | TIMESTAMP | NOT NULL | Default expiry: 7 days (configurable via `jwt.refresh-token-expiry-days`) |
| `revoked_at` | TIMESTAMP | NULL | Set when token is used (rotation) or on logout |

**Indexes:** `idx_rt_user (user_id)`

---

### `login_attempts`

Immutable log of every login attempt for security auditing and lockout tracking.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | NOT NULL | Primary key |
| `username` | VARCHAR(64) | NOT NULL | Attempted username |
| `user_id` | BIGINT | NULL | Resolved user ID if the username exists |
| `success` | BOOLEAN | NOT NULL | |
| `ip_address` | VARCHAR(45) | NULL | IPv4 or IPv6 |
| `user_agent` | VARCHAR(255) | NULL | |
| `attempted_at` | TIMESTAMP | NOT NULL | |

**Indexes:** `idx_la_username_time (username, attempted_at)`

---

### `residents`

Core resident data. Each resident has a unique system-generated `resident_code`.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | NOT NULL | Primary key |
| `resident_code` | VARCHAR(20) | NOT NULL UNIQUE | Format: `RES-YYYY-NNNNNN` |
| `first_name` | VARCHAR(80) | NOT NULL | |
| `middle_name` | VARCHAR(80) | NULL | |
| `last_name` | VARCHAR(80) | NOT NULL | |
| `suffix` | VARCHAR(16) | NULL | e.g., "Jr.", "III" |
| `birthdate` | DATE | NOT NULL | |
| `sex` | ENUM('MALE','FEMALE') | NOT NULL | |
| `civil_status` | ENUM('SINGLE','MARRIED','WIDOWED','SEPARATED','DIVORCED') | NOT NULL | Default `SINGLE` |
| `contact_number` | VARCHAR(20) | NULL | |
| `email` | VARCHAR(128) | NULL | |
| `house_no` | VARCHAR(40) | NULL | |
| `street` | VARCHAR(120) | NULL | |
| `purok_sitio` | VARCHAR(80) | NULL | |
| `household_id` | BIGINT | NULL | FK → `households.id` |
| `occupation` | VARCHAR(120) | NULL | |
| `is_voter` | BOOLEAN | NOT NULL | Default `FALSE` |
| `dup_key` | VARCHAR(200) | NOT NULL | Lowercase `firstName|lastName|birthdate`; used for duplicate detection |
| `created_at` | TIMESTAMP | NOT NULL | |
| `updated_at` | TIMESTAMP | NOT NULL | |
| `created_by` | BIGINT | NULL | |
| `updated_by` | BIGINT | NULL | |
| `deleted_at` | TIMESTAMP | NULL | Soft delete |
| `deleted_by` | BIGINT | NULL | Soft delete |

**Indexes:** `idx_res_lastname (last_name)`, `idx_res_dupkey (dup_key(100), deleted_at)`, `idx_res_household (household_id)`, `idx_res_deleted (deleted_at)`

---

### `households`

Groups residents into a household unit with a single head.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | NOT NULL | Primary key |
| `household_code` | VARCHAR(20) | NOT NULL UNIQUE | Format: `HH-YYYY-NNNNNN` |
| `head_resident_id` | BIGINT | NULL | FK → `residents.id`; one resident can head only one household (enforced at service layer) |
| `house_no` | VARCHAR(40) | NULL | |
| `street` | VARCHAR(120) | NULL | |
| `purok_sitio` | VARCHAR(80) | NULL | |
| `created_at` | TIMESTAMP | NOT NULL | |
| `updated_at` | TIMESTAMP | NOT NULL | |
| `created_by` | BIGINT | NULL | |
| `updated_by` | BIGINT | NULL | |
| `deleted_at` | TIMESTAMP | NULL | Soft delete |
| `deleted_by` | BIGINT | NULL | Soft delete |

**Indexes:** `idx_hh_deleted (deleted_at)`

---

### `household_members`

Tracks which residents belong to which household and their relationship. The household head is also stored here with `relationship = 'HEAD'` when the household is first created.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | NOT NULL | Primary key |
| `household_id` | BIGINT | NOT NULL | FK → `households.id` |
| `resident_id` | BIGINT | NOT NULL | FK → `residents.id` |
| `relationship` | VARCHAR(40) | NOT NULL | e.g., `HEAD`, `MEMBER`; default `MEMBER` |
| `created_at` | TIMESTAMP | NOT NULL | |
| `created_by` | BIGINT | NULL | |
| `deleted_at` | TIMESTAMP | NULL | Soft delete |
| `deleted_by` | BIGINT | NULL | Soft delete |

**Indexes:** `idx_hm_resident (resident_id)`, `idx_hm_household (household_id)`

---

### `clearance_requests`

Tracks barangay clearance requests through their lifecycle: `SUBMITTED → UNDER_REVIEW → APPROVED` (or `→ REJECTED`).

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | NOT NULL | Primary key |
| `control_number` | VARCHAR(24) | NULL UNIQUE | Format: `{prefix}-CLR-YYYY-NNNNNN`; assigned at submission |
| `resident_id` | BIGINT | NOT NULL | FK → `residents.id` |
| `purpose` | VARCHAR(255) | NOT NULL | Stated reason for the clearance |
| `status` | ENUM('SUBMITTED','UNDER_REVIEW','APPROVED','REJECTED') | NOT NULL | Default `SUBMITTED` |
| `remarks` | VARCHAR(500) | NULL | Rejection reason or advisory notes |
| `fee_id` | BIGINT | NULL | FK → `fees.id` |
| `reviewed_by` | BIGINT | NULL | User ID of reviewer |
| `reviewed_at` | TIMESTAMP | NULL | |
| `approved_at` | TIMESTAMP | NULL | |
| `created_at` | TIMESTAMP | NOT NULL | |
| `updated_at` | TIMESTAMP | NOT NULL | |
| `created_by` | BIGINT | NULL | |
| `updated_by` | BIGINT | NULL | |
| `deleted_at` | TIMESTAMP | NULL | Soft delete |
| `deleted_by` | BIGINT | NULL | Soft delete |

**Indexes:** `idx_clr_status (status)`, `idx_clr_resident (resident_id)`, `idx_clr_deleted (deleted_at)`

---

### `clearance_documents`

Stores metadata for generated clearance PDF files. One record is created per approved clearance.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | NOT NULL | Primary key |
| `clearance_id` | BIGINT | NOT NULL | FK → `clearance_requests.id` |
| `control_number` | VARCHAR(24) | NOT NULL | Copied from the clearance at generation time |
| `file_path` | VARCHAR(512) | NOT NULL | Absolute or relative path to the PDF file on disk |
| `sha256_checksum` | CHAR(64) | NOT NULL | SHA-256 of the PDF content; used for integrity verification |
| `issued_at` | TIMESTAMP | NOT NULL | Default `CURRENT_TIMESTAMP` |
| `issued_by` | BIGINT | NULL | User ID of the approver |

**Indexes:** `idx_cd_clr (clearance_id)`

---

### `complaints`

Blotter entries (informal complaints filed at the barangay level). Lifecycle: `FILED → UNDER_MEDIATION → RESOLVED` or `→ ESCALATED`.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | NOT NULL | Primary key |
| `case_number` | VARCHAR(24) | NOT NULL UNIQUE | Format: `{prefix}-BLT-YYYY-NNNNNN` |
| `title` | VARCHAR(160) | NOT NULL | Brief description |
| `narrative` | TEXT | NOT NULL | Full narrative of the complaint |
| `status` | ENUM('FILED','UNDER_MEDIATION','RESOLVED','ESCALATED') | NOT NULL | Default `FILED` |
| `filed_at` | TIMESTAMP | NOT NULL | Default `CURRENT_TIMESTAMP` |
| `resolved_at` | TIMESTAMP | NULL | Set when status transitions to `RESOLVED` |
| `resolution_note` | VARCHAR(500) | NULL | Set when status transitions to `RESOLVED` |
| `handled_by` | BIGINT | NULL | User ID handling the complaint |
| `created_at` | TIMESTAMP | NOT NULL | |
| `updated_at` | TIMESTAMP | NOT NULL | |
| `created_by` | BIGINT | NULL | |
| `updated_by` | BIGINT | NULL | |
| `deleted_at` | TIMESTAMP | NULL | Soft delete |
| `deleted_by` | BIGINT | NULL | Soft delete |

**Indexes:** `idx_cmp_status (status)`, `idx_cmp_deleted (deleted_at)`

---

### `complaint_parties`

Records the individuals involved in a complaint and their roles.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | NOT NULL | Primary key |
| `complaint_id` | BIGINT | NOT NULL | FK → `complaints.id` |
| `resident_id` | BIGINT | NULL | FK → `residents.id`; NULL if party is not a registered resident |
| `party_role` | ENUM('COMPLAINANT','RESPONDENT','WITNESS') | NOT NULL | |
| `display_name` | VARCHAR(160) | NOT NULL | Free-text name shown in reports |
| `created_at` | TIMESTAMP | NOT NULL | |

**Indexes:** `idx_cp_resident (resident_id)`, `idx_cp_complaint (complaint_id)`

---

### `complaint_status_history`

Insert-only log of every status transition for a complaint. Records the from/to statuses, an optional note, and the user who made the change.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | NOT NULL | Primary key |
| `complaint_id` | BIGINT | NOT NULL | FK → `complaints.id` |
| `from_status` | VARCHAR(20) | NULL | NULL for the initial FILED entry |
| `to_status` | VARCHAR(20) | NOT NULL | |
| `note` | VARCHAR(500) | NULL | |
| `changed_by` | BIGINT | NULL | User ID |
| `changed_at` | TIMESTAMP | NOT NULL | Default `CURRENT_TIMESTAMP` |

**Indexes:** `idx_csh_complaint (complaint_id)`

---

### `fees`

Tracks barangay fees. `or_reference` (official receipt number) is assigned only when payment is recorded.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | NOT NULL | Primary key |
| `or_reference` | VARCHAR(24) | NULL UNIQUE | Format: `{prefix}-OR-YYYY-NNNNNN`; assigned on payment |
| `clearance_id` | BIGINT | NULL | FK → `clearance_requests.id` (optional linkage) |
| `fee_type` | VARCHAR(40) | NOT NULL | Default `CLEARANCE` |
| `amount` | DECIMAL(10,2) | NOT NULL | |
| `status` | ENUM('UNPAID','PAID','WAIVED') | NOT NULL | Default `UNPAID` |
| `paid_at` | TIMESTAMP | NULL | Set on payment |
| `collected_by` | BIGINT | NULL | User ID of staff who collected payment |
| `created_at` | TIMESTAMP | NOT NULL | |
| `updated_at` | TIMESTAMP | NOT NULL | |
| `created_by` | BIGINT | NULL | |
| `updated_by` | BIGINT | NULL | |
| `deleted_at` | TIMESTAMP | NULL | Soft delete |
| `deleted_by` | BIGINT | NULL | Soft delete |

**Indexes:** `idx_fee_status (status)`

---

### `audit_log`

Insert-only table. Records every audited operation with before/after state snapshots and a SHA-256 hash chain for tamper detection. The `@Auditable` AOP annotation on service methods triggers inserts automatically.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | NOT NULL | Primary key |
| `entity_type` | VARCHAR(48) | NOT NULL | e.g., `RESIDENT`, `CLEARANCE`, `COMPLAINT`, `FEE`, `USER` |
| `entity_id` | BIGINT | NULL | PK of the affected record |
| `action` | VARCHAR(24) | NOT NULL | e.g., `CREATE`, `UPDATE`, `DELETE`, `APPROVE`, `REJECT`, `PAYMENT`, `STATUS_CHANGE` |
| `actor_user_id` | BIGINT | NULL | User who performed the action |
| `actor_username` | VARCHAR(64) | NULL | Username at time of action |
| `before_json` | JSON | NULL | Entity state before mutation (UPDATE/DELETE only) |
| `after_json` | JSON | NULL | Return value / entity state after the operation |
| `ip_address` | VARCHAR(45) | NULL | Client IP address |
| `prev_hash` | CHAR(64) | NULL | `row_hash` of the previous audit log entry (NULL for first row) |
| `row_hash` | CHAR(64) | NOT NULL | SHA-256 of the concatenated audit fields — forms the hash chain |
| `created_at` | TIMESTAMP | NOT NULL | |

**Indexes:** `idx_audit_entity (entity_type, entity_id)`, `idx_audit_actor (actor_user_id)`, `idx_audit_time (created_at)`

**Note on immutability:** The JPA entity lacks `@Immutable`, meaning ORM-level updates are technically possible if `save()` is called on a managed instance. Immutability is enforced at the database level by revoking UPDATE/DELETE on `audit_log` from the application user. See `V2__db_user_grants.sql` and [KNOWN_ISSUES.md](KNOWN_ISSUES.md).

---

### `document_sequences`

Tracks the last-used sequence value per document type per calendar year. Used by `SequenceService` to generate monotonically increasing, year-scoped document numbers.

| Column | Type | Null | Notes |
|---|---|---|---|
| `doc_type` | VARCHAR(16) | NOT NULL | `RES`, `HH`, `CLR`, `BLT`, `OR` |
| `seq_year` | INT | NOT NULL | Calendar year |
| `last_value` | BIGINT | NOT NULL | Last sequence number issued; default 0 |

**Primary key:** Composite `(doc_type, seq_year)`

---

## Key Relationships

```
users ─────────────────── user_roles ─── roles
  │ resident_id                              
  └──────────────────→ residents ────────────── households (via household_id)
                            │                       │
                            │                  household_members
                            │
                      clearance_requests ──── clearance_documents
                            │
                           fees
                            
                        complaints ──── complaint_parties ──→ residents
                            └──────── complaint_status_history
                            
                         audit_log  (insert-only; no FK to other tables)
                         document_sequences  (standalone counter)
                         login_attempts      (standalone log)
                         refresh_tokens ──→ users
```
