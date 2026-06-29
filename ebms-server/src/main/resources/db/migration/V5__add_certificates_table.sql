-- V5__add_certificates_table.sql

CREATE TABLE certificates (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  control_number    VARCHAR(32)   NOT NULL UNIQUE,
  resident_id       BIGINT        NOT NULL,
  certificate_type  VARCHAR(32)   NOT NULL,
  purpose           VARCHAR(255)  NOT NULL,
  status            VARCHAR(16)   NOT NULL DEFAULT 'REQUESTED',
  remarks           VARCHAR(500)  NULL,
  approved_by       BIGINT        NULL,
  approved_at       TIMESTAMP     NULL,
  file_path         VARCHAR(512)  NULL,
  sha256_checksum   CHAR(64)      NULL,
  created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by        BIGINT        NULL,
  updated_by        BIGINT        NULL,
  deleted_at        TIMESTAMP     NULL,
  deleted_by        BIGINT        NULL,
  INDEX idx_cert_resident  (resident_id),
  INDEX idx_cert_status    (status),
  INDEX idx_cert_deleted   (deleted_at),
  CONSTRAINT fk_cert_resident FOREIGN KEY (resident_id) REFERENCES residents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
