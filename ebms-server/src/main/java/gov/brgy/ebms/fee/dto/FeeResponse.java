package gov.brgy.ebms.fee.dto;

import gov.brgy.ebms.fee.entity.Fee;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FeeResponse(
    Long id,
    String orReference,
    Long clearanceId,
    String feeType,
    BigDecimal amount,
    String status,
    LocalDateTime paidAt,
    LocalDateTime createdAt
) {
    public static FeeResponse from(Fee f) {
        return new FeeResponse(
            f.getId(), f.getOrReference(), f.getClearanceId(),
            f.getFeeType(), f.getAmount(), f.getStatus().name(),
            f.getPaidAt(), f.getCreatedAt()
        );
    }
}
