package gov.brgy.ebms.desktop.controller;

import gov.brgy.ebms.desktop.api.dto.HouseholdDto;
import gov.brgy.ebms.desktop.api.dto.HouseholdRequest;
import gov.brgy.ebms.desktop.core.AsyncRunner;
import gov.brgy.ebms.desktop.core.Dialogs;
import gov.brgy.ebms.desktop.service.EbmsService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class HouseholdFormController {

    @FXML private TextField houseNoField;
    @FXML private TextField streetField;
    @FXML private TextField purokField;
    @FXML private TextField headResidentIdField;
    @FXML private Button saveButton;

    private final EbmsService service = EbmsService.get();
    private HouseholdDto entity;
    private Runnable onSaved;

    public void setEntity(HouseholdDto dto) {
        this.entity = dto;
        if (dto != null) {
            if (houseNoField != null) houseNoField.setText(nullToEmpty(dto.houseNo()));
            if (streetField != null) streetField.setText(nullToEmpty(dto.street()));
            if (purokField != null) purokField.setText(nullToEmpty(dto.purokSitio()));
            if (headResidentIdField != null && dto.headResidentId() != null)
                headResidentIdField.setText(dto.headResidentId().toString());
        }
    }

    public void setOnSaved(Runnable cb) { this.onSaved = cb; }

    @FXML
    public void handleSave() {
        Long headId = null;
        if (headResidentIdField != null && !headResidentIdField.getText().isBlank()) {
            try { headId = Long.parseLong(headResidentIdField.getText().trim()); }
            catch (NumberFormatException e) { Dialogs.error("Head Resident ID must be a number."); return; }
        }
        HouseholdRequest req = new HouseholdRequest(
            headId,
            houseNoField != null ? emptyToNull(houseNoField.getText()) : null,
            streetField != null ? emptyToNull(streetField.getText()) : null,
            purokField != null ? emptyToNull(purokField.getText()) : null
        );

        if (saveButton != null) saveButton.setDisable(true);
        if (entity == null) {
            AsyncRunner.run(
                () -> service.createHousehold(req),
                r -> { if (saveButton != null) saveButton.setDisable(false); if (onSaved != null) onSaved.run(); close(); },
                t -> { if (saveButton != null) saveButton.setDisable(false); Dialogs.handleApiError(t); }
            );
        } else {
            AsyncRunner.run(
                () -> service.updateHousehold(entity.id(), req),
                r -> { if (saveButton != null) saveButton.setDisable(false); if (onSaved != null) onSaved.run(); close(); },
                t -> { if (saveButton != null) saveButton.setDisable(false); Dialogs.handleApiError(t); }
            );
        }
    }

    @FXML
    public void handleCancel() { close(); }

    private void close() {
        if (saveButton != null && saveButton.getScene() != null)
            ((Stage) saveButton.getScene().getWindow()).close();
    }

    private static String nullToEmpty(String s) { return s != null ? s : ""; }
    private static String emptyToNull(String s) { return s != null && !s.isBlank() ? s.trim() : null; }
}
