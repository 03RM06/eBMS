package gov.brgy.ebms.numbering;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class DocumentSequenceId implements Serializable {

    @Column(name = "doc_type", length = 16)
    private String docType;

    @Column(name = "seq_year")
    private int seqYear;

    public DocumentSequenceId() {}

    public DocumentSequenceId(String docType, int seqYear) {
        this.docType = docType;
        this.seqYear = seqYear;
    }

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    public int getSeqYear() { return seqYear; }
    public void setSeqYear(int seqYear) { this.seqYear = seqYear; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentSequenceId that)) return false;
        return seqYear == that.seqYear && Objects.equals(docType, that.docType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(docType, seqYear);
    }
}
