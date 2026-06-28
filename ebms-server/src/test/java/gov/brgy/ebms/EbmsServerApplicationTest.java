package gov.brgy.ebms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration smoke test: verifies Spring context loads with H2 test profile.
 * Flyway is disabled in test; H2 uses create-drop DDL.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "JWT_SECRET=test-secret-key-for-integration-tests-256-bits-long-padded-here",
    "DB_USERNAME=sa",
    "DB_PASSWORD="
})
class EbmsServerApplicationTest {

    @Test
    void contextLoads() {
        // Verifies all Spring beans wire correctly and JPA entity mapping is valid
    }
}
