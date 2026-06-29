package gov.brgy.ebms.pdf;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import gov.brgy.ebms.certificate.entity.Certificate;
import gov.brgy.ebms.certificate.entity.CertificateType;
import gov.brgy.ebms.resident.entity.Resident;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

@Component
public class CertificatePdfGenerator {

    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("MMMM dd, yyyy");

    @Value("${document.storage.path:./documents}")
    private String storagePath;

    @Value("${barangay.name:Barangay Sample}")
    private String barangayName;

    public ClearancePdfGenerator.PdfGenerationResult generate(
            Certificate certificate, Resident resident) throws IOException {
        Path dir = Paths.get(storagePath, "certificates");
        Files.createDirectories(dir);

        String filename = certificate.getControlNumber().replace("/", "-") + ".pdf";
        Path filePath = dir.resolve(filename);

        try (OutputStream out = new FileOutputStream(filePath.toFile())) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
            Font headerFont = new Font(Font.HELVETICA, 12, Font.BOLD);
            Font normalFont = new Font(Font.HELVETICA, 11, Font.NORMAL);

            document.add(new Paragraph(barangayName, titleFont));
            document.add(new Paragraph(titleFor(certificate.getCertificateType()), titleFont));
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("Control No: " + certificate.getControlNumber(), headerFont));
            document.add(new Paragraph("Date Issued: " + LocalDateTime.now().format(DATE_FORMAT), normalFont));
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("TO WHOM IT MAY CONCERN:", headerFont));
            document.add(Chunk.NEWLINE);

            String fullName = resident.getFirstName()
                + (resident.getMiddleName() != null ? " " + resident.getMiddleName() : "")
                + " " + resident.getLastName()
                + (resident.getSuffix() != null ? " " + resident.getSuffix() : "");

            document.add(new Paragraph(bodyFor(certificate.getCertificateType(), fullName), normalFont));
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("Purpose: " + certificate.getPurpose(), normalFont));
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph(
                "This certification is issued upon the request of the above-named person "
                + "for whatever legal purpose it may serve.",
                normalFont
            ));

            document.close();
        }

        String checksum = sha256File(filePath);
        return new ClearancePdfGenerator.PdfGenerationResult(filePath.toString(), checksum);
    }

    private String titleFor(CertificateType type) {
        return switch (type) {
            case INDIGENCY -> "CERTIFICATE OF INDIGENCY";
            case RESIDENCY -> "CERTIFICATE OF RESIDENCY";
            case BUSINESS_PERMIT_ENDORSEMENT -> "BARANGAY BUSINESS PERMIT ENDORSEMENT";
            case GOOD_MORAL -> "CERTIFICATE OF GOOD MORAL CHARACTER";
        };
    }

    private String bodyFor(CertificateType type, String fullName) {
        // Replace placeholder wording with official barangay-approved text before production use.
        return switch (type) {
            case INDIGENCY ->
                "This is to certify that " + fullName + " is a bonafide resident of this barangay "
                + "and belongs to an indigent family who is in need of assistance.";
            case RESIDENCY ->
                "This is to certify that " + fullName + " is a bonafide resident of this barangay "
                + "and has been residing in this area for a considerable period of time.";
            case BUSINESS_PERMIT_ENDORSEMENT ->
                "This is to certify that " + fullName + " is a bonafide resident of this barangay "
                + "and is hereby endorsed to apply for a business permit with the Municipal/City Office.";
            case GOOD_MORAL ->
                "This is to certify that " + fullName + " is a bonafide resident of this barangay "
                + "and is known to be a person of good moral character and standing in the community.";
        };
    }

    private String sha256File(Path filePath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] hash = digest.digest(fileBytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
