package gov.brgy.ebms.household.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "household_members")
@SQLRestriction("deleted_at IS NULL")
public class HouseholdMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "household_id", nullable = false)
    private Long householdId;

    @Column(name = "resident_id", nullable = false)
    private Long residentId;

    @Column(nullable = false, length = 40)
    private String relationship = "MEMBER";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    public HouseholdMember() {}

    public HouseholdMember(Long householdId, Long residentId, String relationship, Long createdBy) {
        this.householdId = householdId;
        this.residentId = residentId;
        this.relationship = relationship;
        this.createdBy = createdBy;
    }

    public void softDelete(Long deletedByUserId) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedByUserId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHouseholdId() { return householdId; }
    public void setHouseholdId(Long householdId) { this.householdId = householdId; }
    public Long getResidentId() { return residentId; }
    public void setResidentId(Long residentId) { this.residentId = residentId; }
    public String getRelationship() { return relationship; }
    public void setRelationship(String relationship) { this.relationship = relationship; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long deletedBy) { this.deletedBy = deletedBy; }
}
