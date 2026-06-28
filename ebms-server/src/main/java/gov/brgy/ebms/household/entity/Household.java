package gov.brgy.ebms.household.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "households")
@SQLRestriction("deleted_at IS NULL")
public class Household {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "household_code", nullable = false, unique = true, length = 20)
    private String householdCode;

    @Column(name = "head_resident_id")
    private Long headResidentId;

    @Column(name = "house_no", length = 40)
    private String houseNo;

    @Column(length = 120)
    private String street;

    @Column(name = "purok_sitio", length = 80)
    private String purokSitio;

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

    public Household() {}

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
    public String getHouseholdCode() { return householdCode; }
    public void setHouseholdCode(String householdCode) { this.householdCode = householdCode; }
    public Long getHeadResidentId() { return headResidentId; }
    public void setHeadResidentId(Long headResidentId) { this.headResidentId = headResidentId; }
    public String getHouseNo() { return houseNo; }
    public void setHouseNo(String houseNo) { this.houseNo = houseNo; }
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getPurokSitio() { return purokSitio; }
    public void setPurokSitio(String purokSitio) { this.purokSitio = purokSitio; }
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
}
