package gov.brgy.ebms.desktop.controller;

import gov.brgy.ebms.desktop.api.ApiException;
import gov.brgy.ebms.desktop.api.dto.ResidentDto;
import gov.brgy.ebms.desktop.api.dto.ResidentRequest;
import gov.brgy.ebms.desktop.core.AsyncRunner;
import gov.brgy.ebms.desktop.core.Dialogs;
import gov.brgy.ebms.desktop.service.EbmsService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public class ResidentFormController {

    @FXML private TextField firstNameField;
    @FXML private TextField middleNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField suffixField;
    @FXML private DatePicker birthdatePicker;
    @FXML private ComboBox<String> sexCombo;
    @FXML private ComboBox<String> civilStatusCombo;
    @FXML private TextField contactField;
    @FXML private TextField emailField;
    @FXML private TextField houseNoField;
    @FXML private TextField streetField;
    @FXML private TextField purokField;
    @FXML private TextField occupationField;
    @FXML private CheckBox voterCheck;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private final EbmsService service = EbmsService.get();
    private ResidentDto entity;
    private Runnable onSaved;
    private boolean retryingDuplicate = false;

    @FXML
    public void initialize() {
        if (sexCombo != null) {
            sexCombo.getItems().setAll("MALE", "FEMALE");
        }
        if (civilStatusCombo != null) {
            civilStatusCombo.getItems().setAll("SINGLE", "MARRIED", "WIDOWED", "SEPARATED", "DIVORCED");
        }
    }

    public void setEntity(ResidentDto dto) {
        this.entity = dto;
        if (dto != null) {
            populate(dto);
        }
    }

    public void setOnSaved(Runnable cb) {
        this.onSaved = cb;
    }

    private void populate(ResidentDto dto) {
        if (firstNameField != null) firstNameField.setText(nullToEmpty(dto.firstName()));
        if (middleNameField != null) middleNameField.setText(nullToEmpty(dto.middleName()));
        if (lastNameField != null) lastNameField.setText(nullToEmpty(dto.lastName()));
        if (suffixField != null) suffixField.setText(nullToEmpty(dto.suffix()));
        if (birthdatePicker != null && dto.birthdate() != null) birthdatePicker.setValue(dto.birthdate());
        if (sexCombo != null) sexCombo.setValue(dto.sex());
        if (civilStatusCombo != null) civilStatusCombo.setValue(dto.civilStatus());
        if (contactField != null) contactField.setText(nullToEmpty(dto.contactNumber()));
        if (emailField != null) emailField.setText(nullToEmpty(dto.email()));
        if (houseNoField != null) houseNoField.setText(nullToEmpty(dto.houseNo()));
        if (streetField != null) streetField.setText(nullToEmpty(dto.street()));
        if (purokField != null) purokField.setText(nullToEmpty(dto.purokSitio()));
        if (occupationField != null) occupationField.setText(nullToEmpty(dto.occupation()));
        if (voterCheck != null) voterCheck.setSelected(dto.isVoter());
    }

    @FXML
    public void handleSave() {
        doSave(false);
    }

    private void doSave(boolean confirmDuplicate) {
        ResidentRequest req = buildRequest(confirmDuplicate);
        if (req == null) return;

        if (saveButton != null) saveButton.setDisable(true);

        if (entity == null) {
            AsyncRunner.run(
                () -> service.createResident(req),
                r -> { onSaveSuccess(); },
                t -> handleSaveError(t)
            );
        } else {
            AsyncRunner.run(
                () -> service.updateResident(entity.id(), req),
                r -> { onSaveSuccess(); },
                t -> handleSaveError(t)
            );
        }
    }

    private void handleSaveError(Throwable t) {
        if (saveButton != null) saveButton.setDisable(false);
        if (t instanceof ApiException ae
                && "DUPLICATE_RESIDENT".equals(ae.getErrorCode())
                && !retryingDuplicate) {
            List<?> candidates = ae.getDuplicateCandidates();
            String names = candidates == null ? "" : candidates.stream()
                .map(Object::toString).collect(Collectors.joining("\n"));
            String msg = "Possible duplicates found:\n" + names + "\n\nSave anyway?";
            if (Dialogs.confirm(msg)) {
                retryingDuplicate = true;
                doSave(true);
            }
        } else {
            retryingDuplicate = false;
            Dialogs.handleApiError(t);
        }
    }

    private void onSaveSuccess() {
        if (saveButton != null) saveButton.setDisable(false);
        if (onSaved != null) onSaved.run();
        closeStage();
    }

    @FXML
    public void handleCancel() {
        closeStage();
    }

    private void closeStage() {
        if (saveButton != null && saveButton.getScene() != null) {
            ((Stage) saveButton.getScene().getWindow()).close();
        }
    }

    private ResidentRequest buildRequest(boolean confirmDuplicate) {
        String firstName = firstNameField != null ? firstNameField.getText().trim() : "";
        String lastName = lastNameField != null ? lastNameField.getText().trim() : "";
        if (firstName.isEmpty() || lastName.isEmpty()) {
            Dialogs.error("First name and last name are required.");
            return null;
        }
        LocalDate birthdate = birthdatePicker != null ? birthdatePicker.getValue() : null;
        if (birthdate == null) {
            Dialogs.error("Birthdate is required.");
            return null;
        }
        return new ResidentRequest(
            firstName,
            middleNameField != null ? middleNameField.getText().trim() : null,
            lastName,
            suffixField != null ? emptyToNull(suffixField.getText()) : null,
            birthdate,
            sexCombo != null ? sexCombo.getValue() : null,
            civilStatusCombo != null ? civilStatusCombo.getValue() : null,
            contactField != null ? emptyToNull(contactField.getText()) : null,
            emailField != null ? emptyToNull(emailField.getText()) : null,
            houseNoField != null ? emptyToNull(houseNoField.getText()) : null,
            streetField != null ? emptyToNull(streetField.getText()) : null,
            purokField != null ? emptyToNull(purokField.getText()) : null,
            null,
            occupationField != null ? emptyToNull(occupationField.getText()) : null,
            voterCheck != null && voterCheck.isSelected(),
            confirmDuplicate
        );
    }

    private static String nullToEmpty(String s) { return s != null ? s : ""; }
    private static String emptyToNull(String s) { return s != null && !s.isBlank() ? s.trim() : null; }
}
