package gov.brgy.ebms.complaint.dto;

import gov.brgy.ebms.complaint.entity.Complaint;

import java.time.LocalDateTime;

public record ComplaintResponse(
    Long id,
    String caseNumber,
    String title,
    String narrative,
    String status,
    LocalDateTime filedAt,
    LocalDateTime resolvedAt,
    String resolutionNote,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static ComplaintResponse from(Complaint c) {
        return new ComplaintResponse(
            c.getId(), c.getCaseNumber(), c.getTitle(), c.getNarrative(),
            c.getStatus().name(), c.getFiledAt(), c.getResolvedAt(),
            c.getResolutionNote(), c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
