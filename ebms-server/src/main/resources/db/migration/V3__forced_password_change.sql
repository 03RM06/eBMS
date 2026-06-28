-- V3__forced_password_change.sql
-- Adds forced_password_change flag to users table.
-- When TRUE, the login response includes requiresPasswordChange: true and the
-- client must redirect the user to change their password before proceeding.

ALTER TABLE users ADD COLUMN forced_password_change BOOLEAN NOT NULL DEFAULT FALSE;

-- Force the default admin account to change password on first login
UPDATE users SET forced_password_change = TRUE WHERE username = 'admin';
