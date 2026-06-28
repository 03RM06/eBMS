-- e-Barangay Management System - Initial Schema
-- V1__init.sql

CREATE TABLE roles (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  code          VARCHAR(32)  NOT NULL UNIQUE,
  name_en       VARCHAR(64)  NOT NULL,
  name_fil      VARCHAR(64)  NOT NULL,
  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE users (
  id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
  username               VARCHAR(64)  NOT NULL,
  email                  VARCHAR(128) NULL,
  password_hash          VARCHAR(72)  NOT NULL,
  full_name              VARCHAR(160) NOT NULL,
  resident_id            BIGINT       NULL,
  preferred_locale       VARCHAR(8)   NOT NULL DEFAULT 'en',
  enabled                BOOLEAN      NOT NULL DEFAULT TRUE,
  failed_login_attempts  INT          NOT NULL DEFAULT 0,
  locked_until           TIMESTAMP    NULL,
  last_login_at          TIMESTAMP    NULL,
  created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by             BIGINT       NULL,
  updated_by             BIGINT       NULL,
  deleted_at             TIMESTAMP    NULL,
  deleted_by             BIGINT       NULL,
  INDEX idx_users_deleted (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_roles (
  user_id   BIGINT NOT NULL,
  role_id   BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refresh_tokens (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id      BIGINT      NOT NULL,
  token_hash   CHAR(64)    NOT NULL UNIQUE,
  issued_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at   TIMESTAMP   NOT NULL,
  revoked_at   TIMESTAMP   NULL,
  CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users(id),
  INDEX idx_rt_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE login_attempts (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  username     VARCHAR(64) NOT NULL,
  user_id      BIGINT      NULL,
  success      BOOLEAN     NOT NULL,
  ip_address   VARCHAR(45) NULL,
  user_agent   VARCHAR(255) NULL,
  attempted_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_la_username_time (username, attempted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE households (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  household_code     VARCHAR(20)  NOT NULL UNIQUE,
  head_resident_id   BIGINT       NULL,
  house_no           VARCHAR(40)  NULL,
  street             VARCHAR(120) NULL,
  purok_sitio        VARCHAR(80)  NULL,
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by         BIGINT       NULL,
  updated_by         BIGINT       NULL,
  deleted_at         TIMESTAMP    NULL,
  deleted_by         BIGINT       NULL,
  INDEX idx_hh_deleted (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE residents (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  resident_code   VARCHAR(20)  NOT NULL UNIQUE,
  first_name      VARCHAR(80)  NOT NULL,
  middle_name     VARCHAR(80)  NULL,
  last_name       VARCHAR(80)  NOT NULL,
  suffix          VARCHAR(16)  NULL,
  birthdate       DATE         NOT NULL,
  sex             ENUM('MALE','FEMALE') NOT NULL,
  civil_status    ENUM('SINGLE','MARRIED','WIDOWED','SEPARATED','DIVORCED') NOT NULL DEFAULT 'SINGLE',
  contact_number  VARCHAR(20)  NULL,
  email           VARCHAR(128) NULL,
  house_no        VARCHAR(40)  NULL,
  street          VARCHAR(120) NULL,
  purok_sitio     VARCHAR(80)  NULL,
  household_id    BIGINT       NULL,
  occupation      VARCHAR(120) NULL,
  is_voter        BOOLEAN      NOT NULL DEFAULT FALSE,
  dup_key         VARCHAR(200) NOT NULL,
  created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by      BIGINT       NULL,
  updated_by      BIGINT       NULL,
  deleted_at      TIMESTAMP    NULL,
  deleted_by      BIGINT       NULL,
  CONSTRAINT fk_res_household FOREIGN KEY (household_id) REFERENCES households(id),
  INDEX idx_res_lastname (last_name),
  INDEX idx_res_dupkey (dup_key(100), deleted_at),
  INDEX idx_res_household (household_id),
  INDEX idx_res_deleted (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE households ADD CONSTRAINT fk_hh_head FOREIGN KEY (head_resident_id) REFERENCES residents(id);
ALTER TABLE users ADD CONSTRAINT fk_users_resident FOREIGN KEY (resident_id) REFERENCES residents(id);

CREATE TABLE household_members (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  household_id    BIGINT      NOT NULL,
  resident_id     BIGINT      NOT NULL,
  relationship    VARCHAR(40) NOT NULL DEFAULT 'MEMBER',
  created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by      BIGINT      NULL,
  deleted_at      TIMESTAMP   NULL,
  deleted_by      BIGINT      NULL,
  CONSTRAINT fk_hm_hh  FOREIGN KEY (household_id) REFERENCES households(id),
  CONSTRAINT fk_hm_res FOREIGN KEY (resident_id) REFERENCES residents(id),
  INDEX idx_hm_resident (resident_id),
  INDEX idx_hm_household (household_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE fees (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  or_reference     VARCHAR(24)  NULL UNIQUE,
  clearance_id     BIGINT       NULL,
  fee_type         VARCHAR(40)  NOT NULL DEFAULT 'CLEARANCE',
  amount           DECIMAL(10,2) NOT NULL,
  status           ENUM('UNPAID','PAID','WAIVED') NOT NULL DEFAULT 'UNPAID',
  paid_at          TIMESTAMP    NULL,
  collected_by     BIGINT       NULL,
  created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by       BIGINT       NULL,
  updated_by       BIGINT       NULL,
  deleted_at       TIMESTAMP    NULL,
  deleted_by       BIGINT       NULL,
  INDEX idx_fee_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE clearance_requests (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  control_number    VARCHAR(24)  NULL UNIQUE,
  resident_id       BIGINT       NOT NULL,
  purpose           VARCHAR(255) NOT NULL,
  status            ENUM('SUBMITTED','UNDER_REVIEW','APPROVED','REJECTED') NOT NULL DEFAULT 'SUBMITTED',
  remarks           VARCHAR(500) NULL,
  fee_id            BIGINT       NULL,
  reviewed_by       BIGINT       NULL,
  reviewed_at       TIMESTAMP    NULL,
  approved_at       TIMESTAMP    NULL,
  created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by        BIGINT       NULL,
  updated_by        BIGINT       NULL,
  deleted_at        TIMESTAMP    NULL,
  deleted_by        BIGINT       NULL,
  CONSTRAINT fk_clr_res  FOREIGN KEY (resident_id) REFERENCES residents(id),
  CONSTRAINT fk_clr_fee  FOREIGN KEY (fee_id) REFERENCES fees(id),
  INDEX idx_clr_status (status),
  INDEX idx_clr_resident (resident_id),
  INDEX idx_clr_deleted (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE clearance_documents (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  clearance_id       BIGINT      NOT NULL,
  control_number     VARCHAR(24) NOT NULL,
  file_path          VARCHAR(512) NOT NULL,
  sha256_checksum    CHAR(64)    NOT NULL,
  issued_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  issued_by          BIGINT      NULL,
  CONSTRAINT fk_cd_clr FOREIGN KEY (clearance_id) REFERENCES clearance_requests(id),
  INDEX idx_cd_clr (clearance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE complaints (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  case_number     VARCHAR(24)  NOT NULL UNIQUE,
  title           VARCHAR(160) NOT NULL,
  narrative       TEXT         NOT NULL,
  status          ENUM('FILED','UNDER_MEDIATION','RESOLVED','ESCALATED') NOT NULL DEFAULT 'FILED',
  filed_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resolved_at     TIMESTAMP    NULL,
  resolution_note VARCHAR(500) NULL,
  handled_by      BIGINT       NULL,
  created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by      BIGINT       NULL,
  updated_by      BIGINT       NULL,
  deleted_at      TIMESTAMP    NULL,
  deleted_by      BIGINT       NULL,
  INDEX idx_cmp_status (status),
  INDEX idx_cmp_deleted (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE complaint_parties (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  complaint_id  BIGINT NOT NULL,
  resident_id   BIGINT NULL,
  party_role    ENUM('COMPLAINANT','RESPONDENT','WITNESS') NOT NULL,
  display_name  VARCHAR(160) NOT NULL,
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_cp_cmp FOREIGN KEY (complaint_id) REFERENCES complaints(id),
  CONSTRAINT fk_cp_res FOREIGN KEY (resident_id) REFERENCES residents(id),
  INDEX idx_cp_resident (resident_id),
  INDEX idx_cp_complaint (complaint_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE complaint_status_history (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  complaint_id  BIGINT NOT NULL,
  from_status   VARCHAR(20) NULL,
  to_status     VARCHAR(20) NOT NULL,
  note          VARCHAR(500) NULL,
  changed_by    BIGINT NULL,
  changed_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_csh_cmp FOREIGN KEY (complaint_id) REFERENCES complaints(id),
  INDEX idx_csh_complaint (complaint_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE audit_log (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  entity_type   VARCHAR(48)  NOT NULL,
  entity_id     BIGINT       NULL,
  action        VARCHAR(24)  NOT NULL,
  actor_user_id BIGINT       NULL,
  actor_username VARCHAR(64) NULL,
  before_json   JSON         NULL,
  after_json    JSON         NULL,
  ip_address    VARCHAR(45)  NULL,
  prev_hash     CHAR(64)     NULL,
  row_hash      CHAR(64)     NOT NULL,
  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_audit_entity (entity_type, entity_id),
  INDEX idx_audit_actor (actor_user_id),
  INDEX idx_audit_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE document_sequences (
  doc_type    VARCHAR(16) NOT NULL,
  seq_year    INT         NOT NULL,
  last_value  BIGINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (doc_type, seq_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO roles (code, name_en, name_fil) VALUES
  ('SUPER_ADMIN',      'Super Administrator',  'Super Administrador'),
  ('BARANGAY_CAPTAIN', 'Barangay Captain',     'Kapitan ng Barangay'),
  ('SECRETARY',        'Secretary',            'Kalihim'),
  ('STAFF',            'Staff',                'Kawani'),
  ('RESIDENT',         'Resident',             'Residente');

-- Default admin user (bcrypt-hashed, strength 12). Password must be changed on first login.
INSERT INTO users (username, password_hash, full_name, enabled, failed_login_attempts, created_at, updated_at)
VALUES ('admin', '$2a$12$OwZNLkhV8MZZ3PEGmBVVKuOt9hAm/C78f1K.y78Pv1/vbGQ0z5hE6', 'System Administrator', TRUE, 0, NOW(), NOW());

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username='admin' AND r.code='SUPER_ADMIN';
