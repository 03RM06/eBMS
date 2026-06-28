package gov.brgy.ebms.desktop.api.dto;

import java.math.BigDecimal;

public record FeeRequest(Long clearanceId, String feeType, BigDecimal amount) {}
