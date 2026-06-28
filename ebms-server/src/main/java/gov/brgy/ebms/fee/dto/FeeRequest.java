package gov.brgy.ebms.fee.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FeeRequest(
    Long clearanceId,
    @Size(max = 40) String feeType,
    @NotNull @DecimalMin("0.00") BigDecimal amount
) {}
