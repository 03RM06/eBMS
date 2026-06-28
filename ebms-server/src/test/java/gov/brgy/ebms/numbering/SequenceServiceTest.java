package gov.brgy.ebms.numbering;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SequenceServiceTest {

    @Mock private DocumentSequenceRepository sequenceRepository;

    @InjectMocks private SequenceService sequenceService;

    @Test
    void nextResidentCode_shouldReturnFormattedCode() {
        int year = LocalDate.now().getYear();

        DocumentSequence seq = new DocumentSequence("RES", year);
        seq.setLastValue(0);

        when(sequenceRepository.findByDocTypeAndYearForUpdate("RES", year))
            .thenReturn(Optional.of(seq));
        when(sequenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String code = sequenceService.nextResidentCode();

        assertThat(code).matches("RES-\\d{4}-\\d{6}");
        assertThat(code).contains(String.valueOf(year));
        assertThat(code).endsWith("-000001");
    }

    @Test
    void nextClearanceNumber_shouldIncludePrefix() {
        int year = LocalDate.now().getYear();

        DocumentSequence seq = new DocumentSequence("CLR", year);
        seq.setLastValue(5);

        when(sequenceRepository.findByDocTypeAndYearForUpdate("CLR", year))
            .thenReturn(Optional.of(seq));
        when(sequenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String code = sequenceService.nextClearanceNumber();

        assertThat(code).contains("CLR");
        assertThat(code).endsWith("-000006");
    }

    @Test
    void nextResidentCode_shouldCreateSequenceIfNotExists() {
        int year = LocalDate.now().getYear();

        when(sequenceRepository.findByDocTypeAndYearForUpdate("RES", year))
            .thenReturn(Optional.empty());

        DocumentSequence newSeq = new DocumentSequence("RES", year);
        when(sequenceRepository.save(any())).thenReturn(newSeq);

        // Should not throw
        sequenceService.nextResidentCode();

        verify(sequenceRepository, atLeastOnce()).save(any());
    }
}
