package gov.brgy.ebms.resident.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "residents")
@SQLRestriction("deleted_at IS NULL")
public class Resident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resident_code", nullable = false, unique = true, length = 20)
    private String residentCode;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "middle_name", length = 80)
    private String middleName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(length = 16)
    private String suffix;

    @Column(nullable = false)
    private LocalDate birthdate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Sex sex;

    @Enumerated(EnumType.STRING)
    @Column(name = "civil_status", nullable = false, length = 16)
    private CivilStatus civilStatus = CivilStatus.SINGLE;

    @Column(name = "contact_number", length = 20)
    private String contactNumber;

    @Column(length = 128)
    private String email;

    @Column(name = "house_no", length = 40)
    private String houseNo;

    @Column(length = 120)
    private String street;

    @Column(name = "purok_sitio", length = 80)
    private String purokSitio;

    @Column(name = "household_id")
    private Long householdId;

    @Column(length = 120)
    private String occupation;

    @Column(name = "is_voter", nullable = false)
    private boolean voter = false;

    @Column(name = "dup_key", nullable = false, length = 200)
    private String dupKey;

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

    public Resident() {}

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void softDelete(Long deletedByUserId) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedByUserId;
    }

    public static String buildDupKey(String firstName, String lastName, LocalDate birthdate) {
        return firstName.toLowerCase().trim() + "|" + lastName.toLowerCase().trim() + "|" + birthdate.toString();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getResidentCode() { return residentCode; }
    public void setResidentCode(String residentCode) { this.residentCode = residentCode; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getSuffix() { return suffix; }
    public void setSuffix(String suffix) { this.suffix = suffix; }
    public LocalDate getBirthdate() { return birthdate; }
    public void setBirthdate(LocalDate birthdate) { this.birthdate = birthdate; }
    public Sex getSex() { return sex; }
    public void setSex(Sex sex) { this.sex = sex; }
    public CivilStatus getCivilStatus() { return civilStatus; }
    public void setCivilStatus(CivilStatus civilStatus) { this.civilStatus = civilStatus; }
    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getHouseNo() { return houseNo; }
    public void setHouseNo(String houseNo) { this.houseNo = houseNo; }
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getPurokSitio() { return purokSitio; }
    public void setPurokSitio(String purokSitio) { this.purokSitio = purokSitio; }
    public Long getHouseholdId() { return householdId; }
    public void setHouseholdId(Long householdId) { this.householdId = householdId; }
    public String getOccupation() { return occupation; }
    public void setOccupation(String occupation) { this.occupation = occupation; }
    public boolean isVoter() { return voter; }
    public void setVoter(boolean voter) { this.voter = voter; }
    public String getDupKey() { return dupKey; }
    public void setDupKey(String dupKey) { this.dupKey = dupKey; }
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

    public enum Sex { MALE, FEMALE }
    public enum CivilStatus { SINGLE, MARRIED, WIDOWED, SEPARATED, DIVORCED }
}
