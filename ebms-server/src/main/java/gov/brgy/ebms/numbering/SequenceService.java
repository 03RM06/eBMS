package gov.brgy.ebms.numbering;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class SequenceService implements DocumentNumberGenerator {

    private final DocumentSequenceRepository sequenceRepository;

    @Value("${barangay.doc.prefix:BRGY}")
    private String barangayPrefix;

    public SequenceService(DocumentSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextResidentCode() {
        return format("RES", "RES-{YYYY}-{NNNNNN}");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextHouseholdCode() {
        return format("HH", "HH-{YYYY}-{NNNNNN}");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextClearanceNumber() {
        return format("CLR", barangayPrefix + "-CLR-{YYYY}-{NNNNNN}");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextBlotterNumber() {
        return format("BLT", barangayPrefix + "-BLT-{YYYY}-{NNNNNN}");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextOrReference() {
        return format("OR", barangayPrefix + "-OR-{YYYY}-{NNNNNN}");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextCertificateNumber() {
        return format("CERT", barangayPrefix + "-CERT-{YYYY}-{NNNNNN}");
    }

    private String format(String docType, String pattern) {
        int year = LocalDate.now().getYear();
        long nextVal = incrementSequence(docType, year);
        return pattern
            .replace("{YYYY}", String.valueOf(year))
            .replace("{NNNNNN}", String.format("%06d", nextVal));
    }

    private long incrementSequence(String docType, int year) {
        DocumentSequence seq = sequenceRepository
            .findByDocTypeAndYearForUpdate(docType, year)
            .orElseGet(() -> sequenceRepository.save(new DocumentSequence(docType, year)));

        long nextVal = seq.getLastValue() + 1;
        seq.setLastValue(nextVal);
        sequenceRepository.save(seq);
        return nextVal;
    }
}
