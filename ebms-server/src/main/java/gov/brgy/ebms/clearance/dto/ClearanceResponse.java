package gov.brgy.ebms.clearance.dto;

import gov.brgy.ebms.clearance.entity.ClearanceRequest;

import java.time.LocalDateTime;

public record ClearanceResponse(
    Long id,
    String controlNumber,
    Long residentId,
    String purpose,
    String status,
    String remarks,
    Long feeId,
    LocalDateTime approvedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static ClearanceResponse from(ClearanceRequest c) {
        return new ClearanceResponse(
            c.getId(), c.getControlNumber(), c.getResidentId(),
            c.getPurpose(), c.getStatus().name(), c.getRemarks(),
            c.getFeeId(), c.getApprovedAt(), c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
