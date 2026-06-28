# Installation and Deployment Guide

## Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| Java | 21+ | Tested on Oracle JDK 25. `java` must be in PATH. |
| MySQL | 8.0+ | Windows service name: `MySQL80` |
| Maven | 3.9+ | Bundled in Apache NetBeans; not required in PATH if building from IDE |

---

## 1. MySQL Database Setup

Connect as a DBA account (e.g., `root`) and run the following:

```sql
CREATE DATABASE ebms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER 'ebms_app'@'localhost' IDENTIFIED BY 'StrongPasswordHere!';
GRANT SELECT, INSERT, UPDATE, DELETE ON ebms.* TO 'ebms_app'@'localhost';

-- Restrict audit_log to INSERT + SELECT to protect immutability
-- (mirrors V2__db_user_grants.sql — Flyway records the migration but the DBA
--  must apply the REVOKE manually because Flyway runs as the app user)
REVOKE UPDATE, DELETE ON ebms.audit_log FROM 'ebms_app'@'localhost';
FLUSH PRIVILEGES;

-- Verify
SHOW GRANTS FOR 'ebms_app'@'localhost';
```

Flyway will create all tables and seed data on first startup (V1–V4 migrations run automatically). Do **not** run the Flyway scripts manually — let the application run them.

> If the DBA has not applied the REVOKE, the application will log a `SECURITY WARNING` at startup via `StartupGrantVerifier`. The system continues to operate, but the audit log will not be immutable at the database level.

---

## 2. Environment Variables

Set these in your shell, a `.env` loader, or as Windows environment variables before starting the server.

### Required

| Variable | Description | Example |
|---|---|---|
| `DB_PASSWORD` | Password for `ebms_app` database user | `StrongPasswordHere!` |
| `JWT_SECRET` | Secret key for signing JWT tokens. Must be at least 32 characters (256-bit). Change from default. | `c94d3f8a1b6e2f9042d5c7a3e1f0b8d2` |

### Optional (with defaults)

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `localhost` | MySQL server hostname |
| `DB_PORT` | `3306` | MySQL server port |
| `DB_NAME` | `ebms` | Database name |
| `DB_USERNAME` | `ebms_app` | Application database username |
| `DB_SSL` | `false` | Enable SSL on JDBC connection (`true` for production) |
| `DB_REQUIRE_SSL` | `false` | Require SSL (`true` for production) |
| `DB_ALLOW_PKR` | `true` | Allow MySQL public key retrieval (set `false` when `DB_SSL=true`) |
| `SSL_ENABLED` | `true` | Enable HTTPS on the embedded server |
| `SSL_KEY_STORE` | `classpath:dev-keystore.p12` | Path to PKCS12 keystore |
| `SSL_KEY_STORE_PASSWORD` | `changeit` | Keystore password |
| `SSL_KEY_ALIAS` | `ebms` | Key alias in the keystore |
| `DOCUMENT_STORAGE_PATH` | `./documents` | Directory where approved clearance PDFs are stored |
| `SERVER_PORT` | `8080` | Embedded server port (set to `8443` for standard HTTPS) |

---

## 3. TLS Keystore Generation

The repository includes a development keystore (`dev-keystore.p12`) suitable for local use only. **Generate a proper keystore for any shared or production environment.**

```shell
keytool -genkeypair `
  -alias ebms `
  -keyalg RSA `
  -keysize 2048 `
  -keystore ebms-keystore.p12 `
  -storetype PKCS12 `
  -validity 365 `
  -storepass YourKeystorePassword `
  -dname "CN=brgy.example.gov.ph,OU=eBMS,O=Barangay,L=Manila,ST=Metro Manila,C=PH"
```

Then set:
```powershell
$env:SSL_KEY_STORE          = "file:C:/path/to/ebms-keystore.p12"
$env:SSL_KEY_STORE_PASSWORD = "YourKeystorePassword"
$env:SSL_KEY_ALIAS          = "ebms"
$env:SSL_ENABLED            = "true"
```

---

## 4. Build

```powershell
# From the repository root
mvn clean package -pl ebms-server -am -DskipTests

# Or to run tests first
mvn clean verify -pl ebms-server -am
```

The executable JAR is produced at `ebms-server/target/ebms-server-1.0.0-SNAPSHOT.jar`.

---

## 5. Running the Server

### Manual start (PowerShell)

```powershell
$env:DB_PASSWORD   = "StrongPasswordHere!"
$env:JWT_SECRET    = "c94d3f8a1b6e2f9042d5c7a3e1f0b8d2a5e8c1f4"
$env:SSL_ENABLED   = "true"
$env:SERVER_PORT   = "8443"

java -jar ebms-server/target/ebms-server-1.0.0-SNAPSHOT.jar `
     --server.port=8443
```

### Running as a Windows Service (NSSM)

[NSSM (Non-Sucking Service Manager)](https://nssm.cc/) wraps any executable as a Windows service.

1. Download `nssm.exe` and place it in `C:\tools\nssm\`.

2. Install the service:

```powershell
C:\tools\nssm\nssm.exe install eBMS "java" `
  "-Dserver.port=8443 -Dssl.enabled=true -jar C:\eBMS\ebms-server-1.0.0-SNAPSHOT.jar"
```

3. Set environment variables for the service:

```powershell
C:\tools\nssm\nssm.exe set eBMS AppEnvironmentExtra `
  "DB_PASSWORD=StrongPasswordHere!" `
  "JWT_SECRET=c94d3f8a1b6e2f9042d5c7a3e1f0b8d2a5e8c1f4" `
  "DB_HOST=localhost" `
  "DOCUMENT_STORAGE_PATH=C:\eBMS\documents"
```

4. Set the working directory:

```powershell
C:\tools\nssm\nssm.exe set eBMS AppDirectory "C:\eBMS"
```

5. Configure stdout/stderr logs:

```powershell
C:\tools\nssm\nssm.exe set eBMS AppStdout "C:\eBMS\logs\ebms-stdout.log"
C:\tools\nssm\nssm.exe set eBMS AppStderr "C:\eBMS\logs\ebms-stderr.log"
```

6. Start the service:

```powershell
C:\tools\nssm\nssm.exe start eBMS
```

To stop or remove:

```powershell
C:\tools\nssm\nssm.exe stop eBMS
C:\tools\nssm\nssm.exe remove eBMS confirm
```

---

## 6. First-Login Steps

On fresh installation, V1 and V3 migrations create a default admin account with a forced password-change flag set.

1. Navigate to the portal at `https://localhost:8443/portal/login` or POST to the API at `/api/v1/auth/login`.

2. Log in with:
   - Username: `admin`
   - Password: `Admin@1234`

3. The login response will include `"requiresPasswordChange": true`. The portal will redirect to a password-change screen. **Change the password immediately before any other action.**

4. After changing the password, log out and log back in to confirm the new credentials work.

5. Create additional user accounts with appropriate roles (`BARANGAY_CAPTAIN`, `SECRETARY`, `STAFF`, `RESIDENT`) before granting access to other staff.

---

## 7. Backup Strategy

### Database

Use `mysqldump` to take regular backups:

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe" `
  --host=localhost `
  --user=root `
  --password `
  --single-transaction `
  --routines `
  --triggers `
  ebms > "C:\eBMS\backups\ebms-$(Get-Date -Format 'yyyyMMdd-HHmm').sql"
```

Schedule this with Windows Task Scheduler. Retain at least 30 days of daily backups.

### PDF Documents

The clearance PDF directory (`DOCUMENT_STORAGE_PATH`, default `./documents`) must be included in backups. Copy it alongside database dumps:

```powershell
Copy-Item -Path "C:\eBMS\documents" `
          -Destination "C:\eBMS\backups\documents-$(Get-Date -Format 'yyyyMMdd')" `
          -Recurse
```

---

## 8. Upgrading

Flyway runs automatically on application startup and applies any new migration scripts in version order. To upgrade:

1. Stop the service.
2. Replace `ebms-server-*.jar` with the new JAR.
3. Start the service.

Flyway will detect unapplied migrations (e.g., V5, V6) and run them. No manual SQL is required unless a migration comment says otherwise (see V2 regarding DBA-level grants).

To check which migrations have been applied, query:

```sql
SELECT version, description, installed_on, success FROM ebms.flyway_schema_history ORDER BY installed_rank;
```
