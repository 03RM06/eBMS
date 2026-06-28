package gov.brgy.ebms.desktop.api.dto;

import java.util.List;

public record ComplaintFilingRequest(
    String title,
    String narrative,
    List<PartyDto> parties
) {}
