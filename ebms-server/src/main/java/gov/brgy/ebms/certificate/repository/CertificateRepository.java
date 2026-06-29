package gov.brgy.ebms.certificate.repository;

import gov.brgy.ebms.certificate.entity.Certificate;
import gov.brgy.ebms.certificate.entity.CertificateStatus;
import gov.brgy.ebms.certificate.entity.CertificateType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    Page<Certificate> findByStatus(CertificateStatus status, Pageable pageable);
    Page<Certificate> findByCertificateType(CertificateType type, Pageable pageable);
    Page<Certificate> findByStatusAndCertificateType(CertificateStatus status, CertificateType type, Pageable pageable);
    Page<Certificate> findByResidentId(Long residentId, Pageable pageable);
}
