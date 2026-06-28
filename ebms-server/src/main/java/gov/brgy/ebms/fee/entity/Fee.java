package gov.brgy.ebms.fee.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fees")
@SQLRestriction("deleted_at IS NULL")
public class Fee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "or_reference", unique = true, length = 24)
    private String orReference;

    @Column(name = "clearance_id")
    private Long clearanceId;

    @Column(name = "fee_type", nullable = false, length = 40)
    private String feeType = "CLEARANCE";

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FeeStatus status = FeeStatus.UNPAID;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "collected_by")
    private Long collectedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    public Fee() {}

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void softDelete(Long deletedByUserId) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedByUserId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrReference() { return orReference; }
    public void setOrReference(String orReference) { this.orReference = orReference; }
    public Long getClearanceId() { return clearanceId; }
    public void setClearanceId(Long clearanceId) { this.clearanceId = clearanceId; }
    public String getFeeType() { return feeType; }
    public void setFeeType(String feeType) { this.feeType = feeType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public FeeStatus getStatus() { return status; }
    public void setStatus(FeeStatus status) { this.status = status; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public Long getCollectedBy() { return collectedBy; }
    public void setCollectedBy(Long collectedBy) { this.collectedBy = collectedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long deletedBy) { this.deletedBy = deletedBy; }

    public enum FeeStatus { UNPAID, PAID, WAIVED }
}
