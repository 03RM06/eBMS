package gov.brgy.ebms.resident;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for AC-012: Required fields validated; birthdate in future rejected with 400
 * + field-level error.
 *
 * NOTE: The implementation currently returns 422 (UNPROCESSABLE_ENTITY) for validation
 * failures, not 400 as specified. The test below documents the spec requirement (400)
 * and WILL FAIL against the current implementation — this is an intentional defect
 * exposure (DEFECT-02).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "JWT_SECRET=test-secret-key-for-integration-tests-256-bits-long-padded-here",
    "DB_USERNAME=sa",
    "DB_PASSWORD="
})
class ResidentValidationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String FUTURE_DATE = LocalDate.now().plusDays(1).toString();
    private static final String PAST_DATE = "1990-05-15";

    /**
     * AC-012: Future birthdate must be rejected.
     * Spec says HTTP 400 with field-level error.
     * Implementation returns 422 — DEFECT-02.
     */
    @Test
    @WithMockUser(roles = "STAFF")
    void create_futureBirthdate_shouldBeRejectedWith400() throws Exception {
        String json = """
            {
              "firstName": "Juan",
              "lastName": "Dela Cruz",
              "birthdate": "%s",
              "sex": "MALE"
            }
            """.formatted(FUTURE_DATE);

        mockMvc.perform(post("/api/v1/residents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest())         // spec: 400
            .andExpect(jsonPath("$.fieldErrors").exists())
            .andExpect(jsonPath("$.fieldErrors.birthdate").exists());
    }

    /**
     * AC-012: Missing last name (required field) must be rejected.
     * Spec says HTTP 400 with field-level error.
     * Implementation returns 422 — DEFECT-02.
     */
    @Test
    @WithMockUser(roles = "STAFF")
    void create_missingLastName_shouldBeRejectedWith400() throws Exception {
        String json = """
            {
              "firstName": "Juan",
              "birthdate": "%s",
              "sex": "MALE"
            }
            """.formatted(PAST_DATE);

        mockMvc.perform(post("/api/v1/residents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest())         // spec: 400
            .andExpect(jsonPath("$.fieldErrors").exists())
            .andExpect(jsonPath("$.fieldErrors.lastName").exists());
    }

    /**
     * AC-012: Exactly-today birthdate (boundary) — @Past rejects today's date.
     * Verifying boundary: today is not in the past.
     */
    @Test
    @WithMockUser(roles = "STAFF")
    void create_todayBirthdate_shouldBeRejected() throws Exception {
        String json = """
            {
              "firstName": "Juan",
              "lastName": "Dela Cruz",
              "birthdate": "%s",
              "sex": "MALE"
            }
            """.formatted(LocalDate.now().toString());

        mockMvc.perform(post("/api/v1/residents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().is4xxClientError());
    }

    /**
     * AC-012: Valid past birthdate with all required fields should NOT be rejected
     * by validation (it might fail further in the stack for other reasons,
     * but must not return 400 for validation).
     */
    @Test
    @WithMockUser(roles = "STAFF")
    void create_validBirthdate_shouldNotReturn400ForValidation() throws Exception {
        String json = """
            {
              "firstName": "Juan",
              "lastName": "Dela Cruz",
              "birthdate": "%s",
              "sex": "MALE",
              "civilStatus": "SINGLE"
            }
            """.formatted(PAST_DATE);

        mockMvc.perform(post("/api/v1/residents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            // May return 201 (success) or some other error, but NOT 400 for validation
            .andExpect(status().is(org.hamcrest.Matchers.not(400)));
    }
}
