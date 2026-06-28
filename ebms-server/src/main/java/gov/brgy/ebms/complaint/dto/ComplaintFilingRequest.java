package gov.brgy.ebms.complaint.dto;

import gov.brgy.ebms.complaint.entity.ComplaintParty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ComplaintFilingRequest(
    @NotBlank @Size(max = 160) String title,
    @NotBlank String narrative,
    @NotEmpty List<PartyDto> parties
) {
    public record PartyDto(
        Long residentId,
        @NotBlank @Size(max = 160) String displayName,
        ComplaintParty.PartyRole partyRole
    ) {}
}
