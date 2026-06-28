package gov.brgy.ebms.clearance;

import gov.brgy.ebms.api.ClearanceController;
import gov.brgy.ebms.clearance.entity.ClearanceDocument;
import gov.brgy.ebms.clearance.service.ClearanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * SEC-5: Verifies that downloadDocument() confines file serving to the configured
 * storage root and rejects paths that traverse outside it.
 */
@ExtendWith(MockitoExtension.class)
class ClearancePathConfinementTest {

    @Mock
    private ClearanceService clearanceService;

    private ClearanceController controller;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        controller = new ClearanceController(clearanceService);
        ReflectionTestUtils.setField(controller, "storagePath", tempDir.toAbsolutePath().toString());
    }

    /**
     * SEC-5: A filePath that resolves outside the storage root must return 404.
     * Covers a path-traversal attempt using ../.. components.
     */
    @Test
    void downloadDocument_pathOutsideStorageRoot_returns404() {
        ClearanceDocument doc = new ClearanceDocument(
            1L, "BRGY-CLR-2026-000001",
            tempDir.resolve("../../etc/passwd").toString(),
            "dummyhash", 1L
        );
        when(clearanceService.getDocument(1L)).thenReturn(doc);

        ResponseEntity<?> response = controller.downloadDocument(1L);

        assertThat(response.getStatusCode())
            .as("SEC-5: path traversal attempt must be rejected with 404")
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * SEC-5: A filePath inside the storage root that does not exist on disk
     * must return 404 (not 500 or leaking path information).
     */
    @Test
    void downloadDocument_pathInsideRootButFileNotFound_returns404() {
        ClearanceDocument doc = new ClearanceDocument(
            2L, "BRGY-CLR-2026-000002",
            tempDir.resolve("nonexistent.pdf").toString(),
            "dummyhash", 1L
        );
        when(clearanceService.getDocument(2L)).thenReturn(doc);

        ResponseEntity<?> response = controller.downloadDocument(2L);

        assertThat(response.getStatusCode())
            .as("SEC-5: missing file within storage root must return 404")
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * SEC-5: A filePath inside the storage root with a real file must succeed.
     */
    @Test
    void downloadDocument_validFileInsideStorageRoot_returns200() throws Exception {
        File pdf = tempDir.resolve("clearance-003.pdf").toFile();
        pdf.createNewFile();

        ClearanceDocument doc = new ClearanceDocument(
            3L, "BRGY-CLR-2026-000003",
            pdf.getAbsolutePath(),
            "dummyhash", 1L
        );
        when(clearanceService.getDocument(3L)).thenReturn(doc);

        ResponseEntity<?> response = controller.downloadDocument(3L);

        assertThat(response.getStatusCode())
            .as("SEC-5: valid file inside storage root must return 200")
            .isEqualTo(HttpStatus.OK);
    }
}
