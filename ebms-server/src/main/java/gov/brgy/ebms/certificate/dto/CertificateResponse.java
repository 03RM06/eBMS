package gov.brgy.ebms.certificate.dto;

import gov.brgy.ebms.certificate.entity.Certificate;

import java.time.LocalDateTime;

public record CertificateResponse(
    Long id,
    String controlNumber,
    Long residentId,
    String certificateType,
    String purpose,
    String status,
    String remarks,
    Long approvedBy,
    LocalDateTime approvedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static CertificateResponse from(Certificate c) {
        return new CertificateResponse(
            c.getId(), c.getControlNumber(), c.getResidentId(),
            c.getCertificateType().name(), c.getPurpose(), c.getStatus().name(),
            c.getRemarks(), c.getApprovedBy(), c.getApprovedAt(),
            c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
