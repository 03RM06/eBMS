package gov.brgy.ebms.complaint.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "complaint_parties")
public class ComplaintParty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", nullable = false)
    private Long complaintId;

    @Column(name = "resident_id")
    private Long residentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_role", nullable = false, length = 20)
    private PartyRole partyRole;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public ComplaintParty() {}

    public ComplaintParty(Long complaintId, Long residentId, PartyRole partyRole, String displayName) {
        this.complaintId = complaintId;
        this.residentId = residentId;
        this.partyRole = partyRole;
        this.displayName = displayName;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getComplaintId() { return complaintId; }
    public void setComplaintId(Long complaintId) { this.complaintId = complaintId; }
    public Long getResidentId() { return residentId; }
    public void setResidentId(Long residentId) { this.residentId = residentId; }
    public PartyRole getPartyRole() { return partyRole; }
    public void setPartyRole(PartyRole partyRole) { this.partyRole = partyRole; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public enum PartyRole { COMPLAINANT, RESPONDENT, WITNESS }
}
