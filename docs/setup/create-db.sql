-- e-Barangay Management System — Initial Database Setup
-- Run as MySQL root or DBA account.
-- Creates the ebms database, application user, and appropriate grants.
-- After running this script, update the application password and set DB_PASSWORD env var.

CREATE DATABASE IF NOT EXISTS ebms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'ebms_app'@'localhost' IDENTIFIED BY 'CHANGE_THIS_PASSWORD';

GRANT SELECT, INSERT, UPDATE, DELETE ON ebms.* TO 'ebms_app'@'localhost';

-- Immutability enforcement at DB level (SEC-FIX-3):
-- Revoke UPDATE and DELETE on audit_log so no application code path can modify records,
-- even if the Java-layer protection is somehow bypassed.
REVOKE UPDATE, DELETE ON ebms.audit_log FROM 'ebms_app'@'localhost';

FLUSH PRIVILEGES;

-- Verify grants (output should NOT show UPDATE/DELETE on audit_log)
SHOW GRANTS FOR 'ebms_app'@'localhost';
