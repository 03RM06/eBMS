package gov.brgy.ebms.resident;

import gov.brgy.ebms.resident.entity.Resident;
import gov.brgy.ebms.resident.repository.ResidentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for AC-013: Duplicate detection on first name + last name + birthdate;
 * 409 returned with duplicateCandidates[]; confirmDuplicate=true override works.
 *
 * DEFECT-01 (High): When a duplicate is detected:
 * - HTTP 500 is returned instead of 409 (DuplicateResidentException falls through to
 *   the generic Exception handler in GlobalExceptionHandler)
 * - The response does not include duplicateCandidates[]
 * - There is no confirmDuplicate=true override mechanism
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "JWT_SECRET=test-secret-key-for-integration-tests-256-bits-long-padded-here",
    "DB_USERNAME=sa",
    "DB_PASSWORD="
})
class ResidentDuplicateHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ResidentRepository residentRepository;

    /**
     * AC-013: Submitting a resident whose name+birthdate matches an existing record
     * must return HTTP 409 with a duplicateCandidates[] array in the response body.
     *
     * DEFECT-01: Currently returns HTTP 500 because DuplicateResidentException
     * is not handled by GlobalExceptionHandler (falls to the generic handler).
     */
    @Test
    @WithMockUser(roles = "STAFF")
    @Transactional
    void create_duplicate_shouldReturn409WithCandidates() throws Exception {
        // Pre-insert a resident to duplicate
        Resident existing = buildResident();
        residentRepository.save(existing);

        String json = """
            {
              "firstName": "Juan",
              "lastName": "Dela Cruz",
              "birthdate": "1990-05-15",
              "sex": "MALE",
              "civilStatus": "SINGLE"
            }
            """;

        mockMvc.perform(post("/api/v1/residents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            // Spec AC-013: must be 409 with duplicateCandidates[]
            // DEFECT-01: currently returns 500
            .andExpect(status().isConflict())                           // 409
            .andExpect(jsonPath("$.duplicateCandidates").exists())      // candidates array
            .andExpect(jsonPath("$.duplicateCandidates").isArray());
    }

    /**
     * AC-013: Submitting the same duplicate with confirmDuplicate=true must
     * bypass the duplicate check and save the resident.
     *
     * DEFECT-01: confirmDuplicate field does not exist in ResidentRequest,
     * and no bypass logic exists in ResidentService. Currently returns 500.
     */
    @Test
    @WithMockUser(roles = "STAFF")
    @Transactional
    void create_duplicateWithConfirmOverride_shouldSucceed() throws Exception {
        // Pre-insert a resident to duplicate
        Resident existing = buildResident();
        residentRepository.save(existing);

        // confirmDuplicate=true should bypass the duplicate check
        String json = """
            {
              "firstName": "Juan",
              "lastName": "Dela Cruz",
              "birthdate": "1990-05-15",
              "sex": "MALE",
              "civilStatus": "SINGLE",
              "confirmDuplicate": true
            }
            """;

        mockMvc.perform(post("/api/v1/residents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            // Spec AC-013: confirmDuplicate=true must succeed with 201
            // DEFECT-01: currently returns 500 (or 422 if JSON binding fails)
            .andExpect(status().isCreated());                           // 201
    }

    private Resident buildResident() {
        Resident r = new Resident();
        r.setResidentCode("RES-TEST-000001");
        r.setFirstName("Juan");
        r.setLastName("Dela Cruz");
        r.setBirthdate(LocalDate.of(1990, 5, 15));
        r.setSex(Resident.Sex.MALE);
        r.setCivilStatus(Resident.CivilStatus.SINGLE);
        r.setDupKey(Resident.buildDupKey("Juan", "Dela Cruz", LocalDate.of(1990, 5, 15)));
        r.setCreatedBy(1L);
        return r;
    }
}
