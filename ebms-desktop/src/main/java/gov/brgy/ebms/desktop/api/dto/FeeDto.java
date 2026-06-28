package gov.brgy.ebms.desktop.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FeeDto(
    Long id,
    String orReference,
    Long clearanceId,
    String feeType,
    BigDecimal amount,
    String status,
    LocalDateTime paidAt,
    LocalDateTime createdAt
) {}
