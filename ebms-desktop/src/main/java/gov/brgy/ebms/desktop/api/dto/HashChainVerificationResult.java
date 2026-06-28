package gov.brgy.ebms.desktop.api.dto;

public record HashChainVerificationResult(boolean valid, long totalRows, Long brokenAtId) {}
