package gov.brgy.ebms.config;

import gov.brgy.ebms.security.jwt.JwtService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = GlobalExceptionHandlerTest.StubController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.StubController.class})
class GlobalExceptionHandlerTest {

    // @EnableJpaAuditing on EbmsServerApplication triggers jpaAuditingHandler in
    // @WebMvcTest context (which has no JPA infrastructure). Mocking the mapping
    // context satisfies the dependency without a real JPA layer.
    @MockBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // JwtAuthenticationFilter is a @Component Filter picked up by @WebMvcTest;
    // its JwtService dependency must be mocked to allow the context to load.
    @MockBean JwtService jwtService;

    @Autowired MockMvc mvc;

    @RestController
    @RequestMapping("/test-stub")
    static class StubController {
        record Body(@NotBlank String name) {}

        @PostMapping("/validate")
        void validate(@Valid @RequestBody Body body) {}

        @GetMapping("/not-found")
        void notFound() { throw new EntityNotFoundException("Item not found"); }

        @GetMapping("/conflict")
        void conflict() { throw new IllegalStateException("Invalid status transition"); }

        @GetMapping("/error")
        void error() { throw new RuntimeException("Internal details"); }
    }

    @Test
    void validationError_returns400WithFieldErrors() throws Exception {
        mvc.perform(post("/test-stub/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Validation failed"))
            .andExpect(jsonPath("$.errors[0]").value("name: must not be blank"))
            .andExpect(jsonPath("$.path").value("/test-stub/validate"));
    }

    @Test
    void entityNotFound_returns404WithMessage() throws Exception {
        mvc.perform(get("/test-stub/not-found"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Item not found"));
    }

    @Test
    void illegalState_returns409WithMessage() throws Exception {
        mvc.perform(get("/test-stub/conflict"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Invalid status transition"));
    }

    @Test
    void genericException_returns500WithGenericMessage_noInternalDetails() throws Exception {
        mvc.perform(get("/test-stub/error"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
