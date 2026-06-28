package gov.brgy.ebms.numbering;

import jakarta.persistence.*;

@Entity
@Table(name = "document_sequences")
public class DocumentSequence {

    @EmbeddedId
    private DocumentSequenceId id;

    @Column(name = "last_value", nullable = false)
    private long lastValue = 0;

    public DocumentSequence() {}

    public DocumentSequence(String docType, int seqYear) {
        this.id = new DocumentSequenceId(docType, seqYear);
        this.lastValue = 0;
    }

    public DocumentSequenceId getId() { return id; }
    public void setId(DocumentSequenceId id) { this.id = id; }
    public long getLastValue() { return lastValue; }
    public void setLastValue(long lastValue) { this.lastValue = lastValue; }
}
