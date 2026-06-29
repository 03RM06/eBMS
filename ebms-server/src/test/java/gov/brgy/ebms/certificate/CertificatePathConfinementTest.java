package gov.brgy.ebms.certificate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CertificatePathConfinementTest {

    @TempDir
    Path storageRoot;

    @Test
    void symlink_escapingStorageRoot_isDetected() throws IOException {
        Path outside = Files.createTempFile("outside", ".pdf");
        outside.toFile().deleteOnExit();

        Path symlink = storageRoot.resolve("escape.pdf");
        Files.createSymbolicLink(symlink, outside);

        Path resolved = symlink.toAbsolutePath().normalize();
        boolean withinRoot = resolved.startsWith(storageRoot.toAbsolutePath().normalize());
        assertThat(withinRoot).as("Symlink target appears inside root before realPath check").isTrue();

        boolean withinRootAfterRealPath = resolved.toRealPath()
            .startsWith(storageRoot.toRealPath());
        assertThat(withinRootAfterRealPath)
            .as("Symlink must NOT pass the realPath confinement check")
            .isFalse();
    }

    @Test
    void legitimateFile_withinStorageRoot_passes() throws IOException {
        Path legitFile = Files.createTempFile(storageRoot, "cert", ".pdf");
        legitFile.toFile().deleteOnExit();

        Path resolved = legitFile.toAbsolutePath().normalize();
        boolean withinRoot = resolved.startsWith(storageRoot.toAbsolutePath().normalize())
            && resolved.toRealPath().startsWith(storageRoot.toRealPath());
        assertThat(withinRoot).isTrue();
    }
}
