package gov.brgy.ebms.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateLocaleRequest(
    @NotBlank(message = "Locale is required")
    @Pattern(regexp = "en|fil", message = "Locale must be 'en' or 'fil'")
    String locale
) {}
