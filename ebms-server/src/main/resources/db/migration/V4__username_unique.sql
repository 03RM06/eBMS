-- FIX-C: Enforce uniqueness on users.username at the database level.
-- Duplicate usernames are a security risk (authentication ambiguity, session confusion).
-- The @Column(unique = true) annotation on User.username covers Hibernate DDL validation;
-- this migration adds the constraint to the existing MySQL schema.
ALTER TABLE users ADD CONSTRAINT uq_users_username UNIQUE (username);
