package gov.brgy.ebms.certificate.dto;

import gov.brgy.ebms.certificate.entity.CertificateType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CertificateRequest(
    @NotNull Long residentId,
    @NotNull CertificateType certificateType,
    @NotBlank @Size(max = 255) String purpose
) {}
