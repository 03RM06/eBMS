# e-Barangay Management System (eBMS) v1

A web-based information system for barangay (village-level local government) administration in the Philippines. eBMS manages resident records, household groupings, barangay clearance requests, complaints (blotter), fee collection, and a tamper-evident audit trail.

## Architecture

eBMS is a Maven multi-module project:

| Module | Role |
|---|---|
| `ebms-server` | Spring Boot 3.3.x backend — REST API + Thymeleaf resident portal |
| `ebms-desktop` | JavaFX 21 desktop client — v1 scaffold (login + dashboard only) |

**Runtime stack:** Java 21, Spring Boot 3.3.5, MySQL 8 (production), H2 (tests), Flyway migrations, OpenPDF, JJWT 0.12.6.

## Prerequisites

- Java 21 or later (tested on JDK 25)
- MySQL 8.0+
- Maven 3.9+ (bundled inside Apache NetBeans if using the IDE)
- A PKCS12 keystore for HTTPS (a dev keystore is included at `ebms-server/src/main/resources/dev-keystore.p12`)

## Quick Start

```shell
# 1. Clone
git clone https://github.com/your-org/eBMS.git
cd eBMS

# 2. Create the database and application user
mysql -u root -p < docs/setup/create-db.sql   # see docs/INSTALLATION.md for SQL

# 3. Set required environment variables (PowerShell example)
$env:DB_PASSWORD    = "YourAppUserPassword"
$env:JWT_SECRET     = "a-256-bit-random-hex-string-goes-here-pad-to-32chars"

# 4. Build
mvn clean package -pl ebms-server -am -DskipTests

# 5. Run
java -jar ebms-server/target/ebms-server-1.0.0-SNAPSHOT.jar
```

The server starts on HTTPS by default. Navigate to `https://localhost:8443` (or whichever port is configured via `server.port`).

> **Default admin credentials — change immediately:**
> Username: `admin` | Password: `Admin@1234`
>
> V3 migration forces a password change on the first login. The `requiresPasswordChange: true`
> flag in the login response tells the client to redirect to a password-change screen before
> granting access. Refer to [docs/INSTALLATION.md](docs/INSTALLATION.md) for the first-login procedure.

## Module Structure

```
eBMS/
├── pom.xml                          # Parent POM (groupId gov.brgy.ebms, v1.0.0-SNAPSHOT)
├── ebms-server/                     # Spring Boot backend
│   ├── src/main/java/gov/brgy/ebms/
│   │   ├── api/                     # REST controllers (/api/v1/**)
│   │   ├── portal/                  # Thymeleaf portal controllers (/portal/**)
│   │   ├── security/                # Auth, JWT, user management
│   │   ├── resident/                # Residents module
│   │   ├── household/               # Households module
│   │   ├── clearance/               # Clearance requests + PDF generation
│   │   ├── complaint/               # Complaints (blotter)
│   │   ├── fee/                     # Fee tracking
│   │   ├── audit/                   # Audit log, AOP aspect, startup grant verifier
│   │   ├── numbering/               # Document sequence service
│   │   └── config/                  # Security, MVC, exception handler
│   └── src/main/resources/
│       ├── db/migration/            # Flyway scripts V1–V4
│       ├── i18n/                    # messages_en.properties, messages_fil.properties
│       └── templates/portal/        # Thymeleaf templates
└── ebms-desktop/                    # JavaFX admin client (v1 scaffold)
    └── src/main/java/gov/brgy/ebms/desktop/
        ├── api/ApiClient.java       # HTTP client wrapping /api/v1/auth/login
        └── controller/              # Login + Dashboard FXML controllers
```

## Roles

Roles are seeded by V1 migration and form a privilege hierarchy:

`SUPER_ADMIN` > `BARANGAY_CAPTAIN` > `SECRETARY` > `STAFF` > `RESIDENT`

Each user can hold one or more roles. The `RESIDENT` role is restricted to read-only access of the user's own linked resident record and their own clearance requests.

## Bilingual Support

The application supports English (`en`) and Filipino (`fil`).

- **Portal:** append `?lang=fil` to any URL, or use the locale switcher.
- **API:** set the `Accept-Language: fil` request header.
- **User preference:** stored in `users.preferred_locale`.

## Documentation

| Document | Contents |
|---|---|
| [docs/INSTALLATION.md](docs/INSTALLATION.md) | Full deployment guide, environment variables, TLS, Windows Service |
| [docs/API.md](docs/API.md) | REST API reference — all endpoints, roles, request/response shapes |
| [docs/SCHEMA.md](docs/SCHEMA.md) | Database schema — all 16 tables, relationships, numbering system |
| [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md) | Known security findings and architectural limitations |
| [CHANGELOG.md](CHANGELOG.md) | Version history |
