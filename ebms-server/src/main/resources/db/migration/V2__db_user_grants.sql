-- V2__db_user_grants.sql
-- Audit log privilege restriction. Must be executed by a DBA with GRANT OPTION.
--
-- Flyway records this migration; the DBA must execute these commands separately:
--
--   CREATE USER 'ebms_app'@'localhost' IDENTIFIED BY '<strong-password>';
--   GRANT SELECT, INSERT, UPDATE, DELETE ON ebms.* TO 'ebms_app'@'localhost';
--
-- Restrict audit_log to INSERT + SELECT only (protect immutability):
--   REVOKE UPDATE, DELETE ON ebms.audit_log FROM 'ebms_app'@'localhost';
--
-- Verify with: SHOW GRANTS FOR 'ebms_app'@'localhost';
--
-- The application's StartupGrantVerifier will log WARNING on startup if the
-- DBA has not yet applied the REVOKE (i.e., UPDATE on audit_log still succeeds).

SELECT 'V2: Audit log privilege restriction — verify DBA applied REVOKE UPDATE/DELETE on audit_log' AS status;
