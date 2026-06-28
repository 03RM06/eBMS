package gov.brgy.ebms.resident;

import gov.brgy.ebms.numbering.DocumentNumberGenerator;
import gov.brgy.ebms.resident.dto.ResidentRequest;
import gov.brgy.ebms.resident.dto.ResidentResponse;
import gov.brgy.ebms.resident.entity.Resident;
import gov.brgy.ebms.resident.repository.ResidentRepository;
import gov.brgy.ebms.resident.service.ResidentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResidentServiceTest {

    @Mock private ResidentRepository residentRepository;
    @Mock private DocumentNumberGenerator documentNumberGenerator;

    @InjectMocks private ResidentService residentService;

    private ResidentRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new ResidentRequest(
            "Juan", "Cruz", "Dela Cruz", null,
            LocalDate.of(1990, 5, 15),
            Resident.Sex.MALE,
            Resident.CivilStatus.SINGLE,
            "09171234567", null, "123", "Main St", "Purok 1",
            null, "Driver", false,
            false   // confirmDuplicate
        );
    }

    @Test
    void create_shouldSucceedWhenNoDuplicate() {
        when(residentRepository.findByDupKey(anyString())).thenReturn(Collections.emptyList());
        when(documentNumberGenerator.nextResidentCode()).thenReturn("RES-2025-000001");

        Resident savedResident = new Resident();
        savedResident.setId(1L);
        savedResident.setResidentCode("RES-2025-000001");
        savedResident.setFirstName("Juan");
        savedResident.setLastName("Dela Cruz");
        savedResident.setBirthdate(LocalDate.of(1990, 5, 15));
        savedResident.setSex(Resident.Sex.MALE);
        savedResident.setCivilStatus(Resident.CivilStatus.SINGLE);
        savedResident.setDupKey("juan|dela cruz|1990-05-15");

        when(residentRepository.save(any(Resident.class))).thenReturn(savedResident);

        ResidentResponse response = residentService.create(validRequest, 1L);

        assertThat(response).isNotNull();
        assertThat(response.residentCode()).isEqualTo("RES-2025-000001");
        assertThat(response.firstName()).isEqualTo("Juan");
    }

    @Test
    void create_shouldThrowWhenDuplicateExists() {
        Resident existing = new Resident();
        existing.setId(99L);
        when(residentRepository.findByDupKey(anyString())).thenReturn(List.of(existing));

        assertThatThrownBy(() -> residentService.create(validRequest, 1L))
            .isInstanceOf(ResidentService.DuplicateResidentException.class)
            .hasMessageContaining("already exists");

        verify(residentRepository, never()).save(any());
    }

    @Test
    void findById_shouldThrowWhenNotFound() {
        when(residentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> residentService.findById(999L))
            .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }
}
