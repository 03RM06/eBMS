# REST API Reference

**Base URL:** `https://localhost:8443/api/v1`
(Port is configurable; set `server.port=8443` or the `SERVER_PORT` env var.)

**Authentication:** All endpoints except `/auth/login` and `/auth/refresh` require:
```
Authorization: Bearer <access_token>
```

**Content-Type:** `application/json` for all request bodies.

**Dates:** ISO 8601 — `LocalDate` fields as `"YYYY-MM-DD"`, `LocalDateTime` fields as `"YYYY-MM-DDTHH:mm:ss"`.

**Bilingual responses:** Include `Accept-Language: fil` for Filipino messages; default is `en`.

**Pagination:** Endpoints returning `Page<T>` accept Spring Data pagination query parameters: `page` (0-indexed), `size`, `sort` (e.g., `sort=lastName,asc`).

---

## Error Responses

All error responses share a common shape:

```json
{ "error": "ERROR_CODE", "message": "Human-readable description" }
```

Common HTTP status codes:

| Code | Meaning |
|---|---|
| 400 | Validation failure — body includes `fieldErrors` map |
| 401 | Not authenticated or credentials invalid |
| 403 | Authenticated but insufficient role |
| 404 | Entity not found |
| 409 | Conflict (e.g., duplicate resident, illegal state transition, fee already paid) |
| 500 | Unexpected server error |

**Validation failure (400) body:**
```json
{
  "error": "VALIDATION_FAILED",
  "fieldErrors": { "firstName": "must not be blank" },
  "timestamp": "2026-06-27T14:00:00"
}
```

**Duplicate resident (409) body:**
```json
{
  "error": "DUPLICATE_RESIDENT",
  "message": "A resident with the same name and birthdate already exists.",
  "duplicateCandidates": [ { ...ResidentResponse... } ]
}
```
To proceed despite a duplicate, re-submit the request with `"confirmDuplicate": true`.

---

## Auth

### POST /auth/login

Authenticate and receive JWT tokens. No `Authorization` header required.

**Request body:**
```json
{
  "username": "secretary01",
  "password": "P@ssw0rd!"
}
```

| Field | Type | Required | Constraint |
|---|---|---|---|
| `username` | string | yes | max 64 chars |
| `password` | string | yes | max 128 chars |

**Response 200:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "userId": 12,
  "username": "secretary01",
  "fullName": "Maria Santos",
  "roles": ["SECRETARY"],
  "requiresPasswordChange": false
}
```

When `requiresPasswordChange` is `true`, the client must direct the user to change their password before granting access to other features.

**Error 401:** Invalid credentials or account locked.

Account lockout triggers after 5 consecutive failures (configurable via `security.max-failed-attempts`). The lock lasts 15 minutes (configurable via `security.lockout-duration-minutes`).

---

### POST /auth/refresh

Exchange a valid refresh token for a new access token and a new refresh token. The old refresh token is immediately revoked (rotation). No `Authorization` header required.

**Request body:**
```json
{ "refreshToken": "550e8400-e29b-41d4-a716-446655440000" }
```

**Response 200:** Same shape as `/auth/login` response.

**Error 401:** Refresh token not found, expired, or already revoked.

---

### POST /auth/logout

Revoke all active refresh tokens for the authenticated user. Returns no body.

**Required role:** Any authenticated user.

**Response 204:** No content.

---

## Residents

### GET /residents

Search or list residents (active only — soft-deleted records are excluded).

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN, SECRETARY, STAFF

**Query parameters:**

| Parameter | Type | Description |
|---|---|---|
| `q` | string | Optional search query (matched against names) |
| `page` | int | Page number, 0-indexed (default 0) |
| `size` | int | Page size (default 20) |
| `sort` | string | Sort field and direction, e.g., `lastName,asc` |

**Response 200 — Page of ResidentResponse:**
```json
{
  "content": [
    {
      "id": 1,
      "residentCode": "RES-2026-000001",
      "firstName": "Juan",
      "middleName": "dela",
      "lastName": "Cruz",
      "suffix": null,
      "birthdate": "1985-03-15",
      "sex": "MALE",
      "civilStatus": "MARRIED",
      "contactNumber": "09171234567",
      "email": "juan@example.com",
      "houseNo": "12",
      "street": "Rizal Street",
      "purokSitio": "Purok 1",
      "householdId": 5,
      "occupation": "Farmer",
      "isVoter": true,
      "createdAt": "2026-01-10T08:30:00",
      "updatedAt": "2026-01-10T08:30:00"
    }
  ],
  "totalElements": 150,
  "totalPages": 8,
  "size": 20,
  "number": 0
}
```

---

### GET /residents/{id}

Retrieve a single resident by ID.

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN, SECRETARY, STAFF, RESIDENT
(RESIDENT role is limited to their own linked resident record via ownership check.)

**Response 200:** ResidentResponse (same shape as above).

**Error 404:** Resident not found (or soft-deleted).

**Error 403:** RESIDENT attempting to access another resident's record.

---

### POST /residents

Create a new resident record.

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN, SECRETARY, STAFF

**Request body:**
```json
{
  "firstName": "Ana",
  "middleName": "Lopez",
  "lastName": "Reyes",
  "suffix": null,
  "birthdate": "1992-07-22",
  "sex": "FEMALE",
  "civilStatus": "SINGLE",
  "contactNumber": "09189876543",
  "email": "ana.reyes@example.com",
  "houseNo": "7B",
  "street": "Mabini Street",
  "purokSitio": "Purok 3",
  "householdId": null,
  "occupation": "Teacher",
  "isVoter": true,
  "confirmDuplicate": false
}
```

| Field | Type | Required | Constraint |
|---|---|---|---|
| `firstName` | string | yes | max 80 chars |
| `middleName` | string | no | max 80 chars |
| `lastName` | string | yes | max 80 chars |
| `suffix` | string | no | max 16 chars (e.g., "Jr.", "III") |
| `birthdate` | date | yes | must be in the past |
| `sex` | string | yes | `MALE` or `FEMALE` |
| `civilStatus` | string | no | `SINGLE`, `MARRIED`, `WIDOWED`, `SEPARATED`, `DIVORCED`; default `SINGLE` |
| `contactNumber` | string | no | max 20 chars |
| `email` | string | no | valid email, max 128 chars |
| `houseNo` | string | no | max 40 chars |
| `street` | string | no | max 120 chars |
| `purokSitio` | string | no | max 80 chars |
| `householdId` | long | no | FK to households.id |
| `occupation` | string | no | max 120 chars |
| `isVoter` | boolean | no | defaults to false |
| `confirmDuplicate` | boolean | no | set `true` to bypass duplicate detection warning |

**Duplicate detection:** If a resident with the same first name, last name, and birthdate exists (case-insensitive), the server returns HTTP 409 with `"error": "DUPLICATE_RESIDENT"` and a `duplicateCandidates` array. Resubmit with `"confirmDuplicate": true` to proceed.

**Response 201:** ResidentResponse of the created record. `residentCode` is auto-generated in the format `RES-YYYY-NNNNNN`.

---

### PUT /residents/{id}

Update an existing resident record. All fields are replaced (full update).

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN, SECRETARY, STAFF

**Request body:** Same as POST /residents.

**Response 200:** Updated ResidentResponse.

**Error 404:** Resident not found.

**Error 409:** Duplicate name+birthdate detected and `confirmDuplicate` not set to `true`.

---

### DELETE /residents/{id}

Soft-delete a resident. The record is retained in the database with `deleted_at` set; it will no longer appear in list or search results.

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN

**Response 204:** No content.

**Error 404:** Resident not found.

---

## Households

### GET /households

List all active households (paginated).

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN, SECRETARY, STAFF

**Query parameters:** Standard pagination (`page`, `size`, `sort`).

**Response 200 — Page of HouseholdResponse:**
```json
{
  "content": [
    {
      "id": 5,
      "householdCode": "HH-2026-000005",
      "headResidentId": 1,
      "houseNo": "12",
      "street": "Rizal Street",
      "purokSitio": "Purok 1",
      "createdAt": "2026-01-10T08:00:00",
      "updatedAt": "2026-01-10T08:00:00"
    }
  ],
  "totalElements": 45,
  "totalPages": 3,
  "size": 20,
  "number": 0
}
```

---

### GET /households/{id}

Retrieve a single household by ID.

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN, SECRETARY, STAFF

**Response 200:** HouseholdResponse.

**Error 404:** Household not found.

---

### POST /households

Create a new household. The system auto-generates `householdCode` in the format `HH-YYYY-NNNNNN`. If `headResidentId` is provided, the resident is also added as a `HEAD` member in `household_members`.

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN, SECRETARY, STAFF

**Request body:**
```json
{
  "headResidentId": 1,
  "houseNo": "12",
  "street": "Rizal Street",
  "purokSitio": "Purok 1"
}
```

| Field | Type | Required | Constraint |
|---|---|---|---|
| `headResidentId` | long | no | FK to residents.id; resident must not already head another household |
| `houseNo` | string | no | max 40 chars |
| `street` | string | no | max 120 chars |
| `purokSitio` | string | no | max 80 chars |

**Response 201:** HouseholdResponse.

**Error 400:** `headResidentId` already heads another household.

---

### PUT /households/{id}

Update address details or reassign the head of household.

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN, SECRETARY, STAFF

**Request body:** Same as POST /households.

**Response 200:** Updated HouseholdResponse.

**Error 400:** New `headResidentId` already heads another household.

---

### DELETE /households/{id}

Soft-delete a household.

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN

**Response 204:** No content.

---

## Clearances

### GET /clearances

List clearance requests, optionally filtered by status.

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN, SECRETARY, STAFF

**Query parameters:**

| Parameter | Type | Description |
|---|---|---|
| `status` | string | Optional filter: `SUBMITTED`, `UNDER_REVIEW`, `APPROVED`, `REJECTED` |
| `page`, `size`, `sort` | | Standard pagination |

**Response 200 — Page of ClearanceResponse:**
```json
{
  "content": [
    {
      "id": 7,
      "controlNumber": "BRGY-CLR-2026-000007",
      "residentId": 1,
      "purpose": "Employment",
      "status": "SUBMITTED",
      "remarks": null,
      "feeId": null,
      "approvedAt": null,
      "createdAt": "2026-06-27T10:00:00",
      "updatedAt": "2026-06-27T10:00:00"
    }
  ],
  "totalElements": 23,
  "totalPages": 2,
  "size": 20,
  "number": 0
}
```

---

### GET /clearances/{id}

Retrieve a single clearance request.

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN, SECRETARY, STAFF, RESIDENT
(RESIDENT callers may only access their own clearance requests.)

**Response 200:** ClearanceResponse.

**Error 403:** RESIDENT attempting to access another resident's clearance.

**Error 404:** Clearance not found.

---

### POST /clearances

Submit a new clearance request. `controlNumber` is auto-assigned in the format `{prefix}-CLR-YYYY-NNNNNN` (prefix defaults to `BRGY`, configurable via `barangay.doc.prefix`).

If the resident already has a SUBMITTED or UNDER_REVIEW clearance, a warning is placed in `remarks` (non-blocking).

**Required role:** Any authenticated role.
(RESIDENT callers may only submit for their own linked resident record.)

**Request body:**
```json
{
  "residentId": 1,
  "purpose": "Employment"
}
```

| Field | Type | Required | Constraint |
|---|---|---|---|
| `residentId` | long | yes | FK to residents.id |
| `purpose` | string | yes | max 255 chars |

**Response 201:** ClearanceResponse with `status: "SUBMITTED"`.

**Error 403:** RESIDENT attempting to submit for a different resident.

---

### POST /clearances/{id}/review

Move a clearance from `SUBMITTED` to `UNDER_REVIEW`. The caller is recorded as the reviewer.

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN, SECRETARY

**Response 200:** Updated ClearanceResponse with `status: "UNDER_REVIEW"`.

**Error 409:** Clearance is not in `SUBMITTED` status.

---

### POST /clearances/{id}/approve

Approve a clearance that is `UNDER_REVIEW`. On approval:
- Status changes to `APPROVED`
- `approvedAt` is set to the current timestamp
- A PDF is generated and stored; a `clearance_documents` record is created with `sha256_checksum`
- If the resident has unresolved complaints, a warning is appended to `remarks` (approval is not blocked)

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN, SECRETARY

**Response 200:** Updated ClearanceResponse with `status: "APPROVED"`.

**Error 403:** Approver is the same resident as the clearance subject (self-approval prevention).

**Error 409:** Clearance is not in `UNDER_REVIEW` status.

---

### POST /clearances/{id}/reject

Reject a clearance. Can be rejected from any status except `APPROVED`.

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN, SECRETARY

**Query parameter:**

| Parameter | Type | Description |
|---|---|---|
| `remarks` | string | Optional reason for rejection |

**Response 200:** Updated ClearanceResponse with `status: "REJECTED"`.

**Error 409:** Clearance is already `APPROVED`.

---

## Complaints

### GET /complaints

List complaints, optionally filtered by status.

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN, SECRETARY, STAFF

**Query parameters:**

| Parameter | Type | Description |
|---|---|---|
| `status` | string | Optional filter: `FILED`, `UNDER_MEDIATION`, `RESOLVED`, `ESCALATED` |
| `page`, `size`, `sort` | | Standard pagination |

**Response 200 — Page of ComplaintResponse:**
```json
{
  "content": [
    {
      "id": 3,
      "caseNumber": "BRGY-BLT-2026-000003",
      "title": "Noise disturbance",
      "narrative": "Respondent plays loud music past midnight on weeknights.",
      "status": "FILED",
      "filedAt": "2026-06-25T09:15:00",
      "resolvedAt": null,
      "resolutionNote": null,
      "createdAt": "2026-06-25T09:15:00",
      "updatedAt": "2026-06-25T09:15:00"
    }
  ],
  "totalElements": 12,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

---

### GET /complaints/{id}

Retrieve a single complaint by ID.

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN, SECRETARY, STAFF

**Response 200:** ComplaintResponse.

**Error 404:** Complaint not found (or soft-deleted).

---

### POST /complaints

File a new complaint (blotter entry). `caseNumber` is auto-assigned in the format `{prefix}-BLT-YYYY-NNNNNN`. The initial status is `FILED`. A `complaint_status_history` entry is created automatically.

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN, SECRETARY, STAFF

**Request body:**
```json
{
  "title": "Noise disturbance",
  "narrative": "Respondent plays loud music past midnight on weeknights.",
  "parties": [
    {
      "residentId": 10,
      "displayName": "Pedro Villanueva",
      "partyRole": "COMPLAINANT"
    },
    {
      "residentId": 15,
      "displayName": "Roberto Delos Reyes",
      "partyRole": "RESPONDENT"
    }
  ]
}
```

| Field | Type | Required | Constraint |
|---|---|---|---|
| `title` | string | yes | max 160 chars |
| `narrative` | string | yes | no length limit |
| `parties` | array | yes | at least one party required |
| `parties[].residentId` | long | no | FK to residents.id; may be null for non-residents |
| `parties[].displayName` | string | yes | max 160 chars |
| `parties[].partyRole` | string | yes | `COMPLAINANT`, `RESPONDENT`, or `WITNESS` |

**Response 201:** ComplaintResponse.

---

### POST /complaints/{id}/transition

Advance the complaint through its status lifecycle. A `complaint_status_history` entry is created for each transition.

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN, SECRETARY

**Query parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `newStatus` | string | yes | Target status |
| `note` | string | no | Transition note; used as `resolution_note` when transitioning to `RESOLVED` |

**Valid transitions:**

| From | Allowed `newStatus` |
|---|---|
| `FILED` | `UNDER_MEDIATION`, `ESCALATED` |
| `UNDER_MEDIATION` | `RESOLVED`, `ESCALATED` |
| `RESOLVED` | _(terminal — no further transitions)_ |
| `ESCALATED` | _(terminal — no further transitions)_ |

**Response 200:** Updated ComplaintResponse.

**Error 409:** Transition is not valid for the current status.

---

### DELETE /complaints/{id}

Soft-delete a complaint record.

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN

**Response 204:** No content.

---

## Fees

### GET /fees/unpaid

List all fees with status `UNPAID`. Returns a plain array (not paginated).

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN, SECRETARY, STAFF

**Response 200:**
```json
[
  {
    "id": 4,
    "orReference": null,
    "clearanceId": 7,
    "feeType": "CLEARANCE",
    "amount": 50.00,
    "status": "UNPAID",
    "paidAt": null,
    "createdAt": "2026-06-27T10:05:00"
  }
]
```

---

### GET /fees/{id}

Retrieve a single fee record.

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN, SECRETARY, STAFF

**Response 200:** FeeResponse (same shape as above).

**Error 404:** Fee not found.

---

### POST /fees

Create a new fee record.

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN, SECRETARY, STAFF

**Request body:**
```json
{
  "clearanceId": 7,
  "feeType": "CLEARANCE",
  "amount": 50.00
}
```

| Field | Type | Required | Constraint |
|---|---|---|---|
| `clearanceId` | long | no | FK to clearance_requests.id |
| `feeType` | string | no | max 40 chars; defaults to `"CLEARANCE"` |
| `amount` | decimal | yes | min 0.00 |

**Response 201:** FeeResponse with `status: "UNPAID"`. `orReference` is null until payment.

---

### POST /fees/{id}/pay

Mark a fee as paid. Assigns an official receipt (OR) number in the format `{prefix}-OR-YYYY-NNNNNN`, records `paidAt`, and records the collecting user.

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN, SECRETARY, STAFF

**Response 200:** Updated FeeResponse with `status: "PAID"` and `orReference` populated.

**Error 409:** Fee is already paid.

---

### POST /fees/{id}/waive

Waive a fee (status changes to `WAIVED`).

**Required role:** SUPER_ADMIN, BARANGAY_CAPTAIN

**Response 200:** Updated FeeResponse with `status: "WAIVED"`.

---

## Portal (Thymeleaf Web Portal)

The portal is a server-rendered Thymeleaf application under `/portal/**`. It uses session-based authentication (form login), not JWT. It is intended for resident self-service.

| Path | Method | Description |
|---|---|---|
| `/portal/login` | GET | Login page |
| `/portal/login` | POST | Authenticate (form fields: `username`, `password`) |
| `/portal/logout` | POST | Log out, invalidates session |
| `/portal/dashboard` | GET | Landing page after login |
| `/portal/resident/profile` | GET | Resident's own profile |
| `/portal/resident/clearances` | GET | Resident's own clearance history |
| `/portal/resident/clearances` | POST | Submit a new clearance request (form fields: `residentId`, `purpose`) |
| `/portal/locale` | GET/POST | Switch display language (`?lang=en` or `?lang=fil`) |

Portal sessions time out after 30 minutes of inactivity. Session cookies are `HttpOnly`, `Secure`, and `SameSite=Lax`.

---

## Endpoints Specified but Not Yet Implemented

The following endpoints were planned for v1 but do not have corresponding REST controller mappings in the current codebase. They are tracked for a future release.

| Endpoint | Notes |
|---|---|
| `GET /auth/me` | Return the authenticated user's profile |
| `PUT /auth/me/locale` | Update the authenticated user's preferred locale |
| `PATCH /auth/change-password` | Change own password (forced-change flow) |
| `POST /residents/{id}/restore` | Restore a soft-deleted resident |
| `POST /households/{id}/members` | Add a member to a household (service method exists: `HouseholdService.addMember()`) |
| `DELETE /households/{id}/members/{residentId}` | Remove a member from a household |
| `PUT /households/{id}/head` | Change the head of a household |
| `GET /clearances/{id}/document` | Download the approved clearance PDF |
| `GET /complaints/unresolved` | List all unresolved complaints |
| `GET /audit` | Paginated audit log |
| `GET /audit/verify` | Verify audit hash chain integrity |
| `GET /config/i18n/{locale}` | Retrieve i18n message bundle |
| `GET /config/public` | Retrieve public configuration (barangay name, doc prefix) |
