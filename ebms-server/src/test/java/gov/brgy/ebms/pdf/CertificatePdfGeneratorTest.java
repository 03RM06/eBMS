package gov.brgy.ebms.pdf;

import gov.brgy.ebms.certificate.entity.Certificate;
import gov.brgy.ebms.certificate.entity.CertificateType;
import gov.brgy.ebms.resident.entity.Resident;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CertificatePdfGeneratorTest {

    @TempDir
    Path tempDir;

    private CertificatePdfGenerator generator() {
        CertificatePdfGenerator gen = new CertificatePdfGenerator();
        ReflectionTestUtils.setField(gen, "storagePath", tempDir.toString());
        ReflectionTestUtils.setField(gen, "barangayName", "Test Barangay");
        return gen;
    }

    private Resident sampleResident() {
        Resident r = new Resident();
        r.setFirstName("Juan");
        r.setMiddleName("Santos");
        r.setLastName("Dela Cruz");
        r.setSuffix(null);
        return r;
    }

    private Certificate sampleCertificate(CertificateType type) {
        Certificate c = new Certificate();
        c.setControlNumber("TEST-CERT-2026-000001");
        c.setCertificateType(type);
        c.setPurpose("For employment");
        return c;
    }

    @ParameterizedTest
    @EnumSource(CertificateType.class)
    void generate_allTypes_producesNonEmptyPdf(CertificateType type) throws IOException {
        ClearancePdfGenerator.PdfGenerationResult result =
            generator().generate(sampleCertificate(type), sampleResident());

        assertThat(result.filePath()).isNotBlank();
        assertThat(new java.io.File(result.filePath())).exists().isNotEmpty();
        assertThat(result.sha256Checksum()).hasSize(64);
    }

    @Test
    void generate_fileNameContainsControlNumber() throws IOException {
        ClearancePdfGenerator.PdfGenerationResult result =
            generator().generate(sampleCertificate(CertificateType.INDIGENCY), sampleResident());

        assertThat(result.filePath()).contains("TEST-CERT-2026-000001");
    }
}
