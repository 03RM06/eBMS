package gov.brgy.ebms.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for AC-004: API returns 401/403 as JSON, never as HTML redirect.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "JWT_SECRET=test-secret-key-for-integration-tests-256-bits-long-padded-here",
    "DB_USERNAME=sa",
    "DB_PASSWORD="
})
class SecurityJsonResponseTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * AC-004: Request with no token to /api/v1/** must get 401 JSON, not HTML redirect.
     */
    @Test
    void unauthenticated_apiRequest_shouldReturn401Json() throws Exception {
        mockMvc.perform(get("/api/v1/residents"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").exists())
            .andExpect(content().string(not(containsString("<html"))));
    }

    /**
     * AC-004: Request with malformed/invalid Bearer token must get 401 JSON.
     */
    @Test
    void invalidToken_apiRequest_shouldReturn401Json() throws Exception {
        mockMvc.perform(get("/api/v1/residents")
                .header("Authorization", "Bearer not.a.real.token"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().string(not(containsString("<html"))));
    }

    /**
     * AC-004: Portal endpoints (/portal/**) should redirect to login page (HTML), not JSON.
     * This documents expected behaviour: portal uses form login, not JWT.
     */
    @Test
    void unauthenticated_portalRequest_shouldRedirectToLoginNotReturn401Json() throws Exception {
        mockMvc.perform(get("/portal/dashboard"))
            .andExpect(status().is3xxRedirection());
    }

    /**
     * AC-004: Login endpoint /api/v1/auth/login must be publicly accessible (no 401).
     */
    @Test
    void loginEndpoint_shouldBePublicAndReturn400OnBadBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"\"}"))
            .andExpect(status().is(not(401)))
            .andExpect(status().is(not(403)));
    }
}
