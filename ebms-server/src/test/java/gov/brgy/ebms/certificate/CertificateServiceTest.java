package gov.brgy.ebms.certificate;

import gov.brgy.ebms.certificate.dto.CertificateRequest;
import gov.brgy.ebms.certificate.dto.CertificateResponse;
import gov.brgy.ebms.certificate.entity.Certificate;
import gov.brgy.ebms.certificate.entity.CertificateStatus;
import gov.brgy.ebms.certificate.entity.CertificateType;
import gov.brgy.ebms.certificate.repository.CertificateRepository;
import gov.brgy.ebms.certificate.service.CertificateService;
import gov.brgy.ebms.numbering.DocumentNumberGenerator;
import gov.brgy.ebms.pdf.CertificatePdfGenerator;
import gov.brgy.ebms.pdf.ClearancePdfGenerator;
import gov.brgy.ebms.resident.entity.Resident;
import gov.brgy.ebms.resident.repository.ResidentRepository;
import gov.brgy.ebms.security.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    @Mock CertificateRepository certificateRepository;
    @Mock ResidentRepository residentRepository;
    @Mock DocumentNumberGenerator documentNumberGenerator;
    @Mock CertificatePdfGenerator pdfGenerator;
    @Mock UserRepository userRepository;

    @InjectMocks CertificateService certificateService;

    @Test
    void submit_happyPath_returnsCertificateWithRequestedStatus() {
        CertificateRequest req = new CertificateRequest(1L, CertificateType.INDIGENCY, "For scholarship");
        when(residentRepository.findById(1L)).thenReturn(Optional.of(new Resident()));
        when(documentNumberGenerator.nextCertificateNumber()).thenReturn("BRGY-CERT-2026-000001");

        Certificate saved = buildCertificate(1L, 1L, CertificateStatus.REQUESTED);
        saved.setControlNumber("BRGY-CERT-2026-000001");
        when(certificateRepository.save(any())).thenReturn(saved);

        CertificateResponse response = certificateService.submit(req, 99L);

        assertThat(response.status()).isEqualTo("REQUESTED");
        assertThat(response.controlNumber()).isEqualTo("BRGY-CERT-2026-000001");
        verify(documentNumberGenerator).nextCertificateNumber();
    }

    @Test
    void submit_residentNotFound_throwsEntityNotFoundException() {
        when(residentRepository.findById(99L)).thenReturn(Optional.empty());
        CertificateRequest req = new CertificateRequest(99L, CertificateType.INDIGENCY, "Purpose");

        assertThatThrownBy(() -> certificateService.submit(req, 1L))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("99");
    }

    @Test
    void approve_happyPath_setsStatusApprovedAndGeneratesPdf() throws IOException {
        Certificate cert = buildCertificate(1L, 1L, CertificateStatus.REQUESTED);
        cert.setCertificateType(CertificateType.RESIDENCY);
        when(certificateRepository.findById(1L)).thenReturn(Optional.of(cert));
        when(residentRepository.findById(1L)).thenReturn(Optional.of(new Resident()));

        File tmpPdf = File.createTempFile("cert-test", ".pdf");
        tmpPdf.deleteOnExit();
        when(pdfGenerator.generate(any(), any()))
            .thenReturn(new ClearancePdfGenerator.PdfGenerationResult(tmpPdf.getAbsolutePath(), "abc123"));
        when(certificateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CertificateResponse response = certificateService.approve(1L, 5L);

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.approvedBy()).isEqualTo(5L);
    }

    @Test
    void approve_whenNotRequested_throwsIllegalStateException() {
        Certificate cert = buildCertificate(1L, 1L, CertificateStatus.APPROVED);
        when(certificateRepository.findById(1L)).thenReturn(Optional.of(cert));

        assertThatThrownBy(() -> certificateService.approve(1L, 5L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("APPROVED");
    }

    @Test
    void reject_happyPath_setsStatusRejectedWithRemarks() {
        Certificate cert = buildCertificate(1L, 1L, CertificateStatus.REQUESTED);
        when(certificateRepository.findById(1L)).thenReturn(Optional.of(cert));
        when(certificateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CertificateResponse response = certificateService.reject(1L, "Incomplete docs", 5L);

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.remarks()).isEqualTo("Incomplete docs");
    }

    @Test
    void reject_whenAlreadyApproved_throwsIllegalStateException() {
        Certificate cert = buildCertificate(1L, 1L, CertificateStatus.APPROVED);
        when(certificateRepository.findById(1L)).thenReturn(Optional.of(cert));

        assertThatThrownBy(() -> certificateService.reject(1L, "reason", 5L))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getFilePath_whenNotApproved_throwsIllegalStateException() {
        Certificate cert = buildCertificate(1L, 1L, CertificateStatus.REQUESTED);
        when(certificateRepository.findById(1L)).thenReturn(Optional.of(cert));

        assertThatThrownBy(() -> certificateService.getFilePath(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not yet approved");
    }

    private Certificate buildCertificate(Long id, Long residentId, CertificateStatus status) {
        Certificate c = new Certificate();
        c.setId(id);
        c.setResidentId(residentId);
        c.setStatus(status);
        c.setCertificateType(CertificateType.INDIGENCY);
        c.setPurpose("Test purpose");
        c.setControlNumber("BRGY-CERT-2026-" + String.format("%06d", id));
        return c;
    }
}
