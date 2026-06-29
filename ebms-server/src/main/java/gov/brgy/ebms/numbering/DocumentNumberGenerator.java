package gov.brgy.ebms.numbering;

/**
 * Interface for document number generation, allowing easy mocking in tests.
 */
public interface DocumentNumberGenerator {
    String nextResidentCode();
    String nextHouseholdCode();
    String nextClearanceNumber();
    String nextBlotterNumber();
    String nextOrReference();
    String nextCertificateNumber();
}
