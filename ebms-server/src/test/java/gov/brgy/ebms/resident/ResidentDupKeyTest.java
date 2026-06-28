package gov.brgy.ebms.resident;

import gov.brgy.ebms.resident.entity.Resident;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ResidentDupKeyTest {

    @Test
    void buildDupKey_shouldBeCaseInsensitiveAndNormalized() {
        String key1 = Resident.buildDupKey("JUAN", "DELA CRUZ", LocalDate.of(1990, 5, 15));
        String key2 = Resident.buildDupKey("juan", "dela cruz", LocalDate.of(1990, 5, 15));
        assertThat(key1).isEqualTo(key2);
    }

    @Test
    void buildDupKey_shouldIncludeAllComponents() {
        String key = Resident.buildDupKey("Maria", "Santos", LocalDate.of(2000, 1, 1));
        assertThat(key).contains("maria");
        assertThat(key).contains("santos");
        assertThat(key).contains("2000");
    }
}
