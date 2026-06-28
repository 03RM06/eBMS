package gov.brgy.ebms.clearance.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "clearance_documents")
public class ClearanceDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clearance_id", nullable = false)
    private Long clearanceId;

    @Column(name = "control_number", nullable = false, length = 24)
    private String controlNumber;

    @Column(name = "file_path", nullable = false, length = 512)
    private String filePath;

    @Column(name = "sha256_checksum", nullable = false, length = 64)
    private String sha256Checksum;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt = LocalDateTime.now();

    @Column(name = "issued_by")
    private Long issuedBy;

    public ClearanceDocument() {}

    public ClearanceDocument(Long clearanceId, String controlNumber, String filePath, String sha256Checksum, Long issuedBy) {
        this.clearanceId = clearanceId;
        this.controlNumber = controlNumber;
        this.filePath = filePath;
        this.sha256Checksum = sha256Checksum;
        this.issuedBy = issuedBy;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getClearanceId() { return clearanceId; }
    public void setClearanceId(Long clearanceId) { this.clearanceId = clearanceId; }
    public String getControlNumber() { return controlNumber; }
    public void setControlNumber(String controlNumber) { this.controlNumber = controlNumber; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getSha256Checksum() { return sha256Checksum; }
    public void setSha256Checksum(String sha256Checksum) { this.sha256Checksum = sha256Checksum; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }
    public Long getIssuedBy() { return issuedBy; }
    public void setIssuedBy(Long issuedBy) { this.issuedBy = issuedBy; }
}
