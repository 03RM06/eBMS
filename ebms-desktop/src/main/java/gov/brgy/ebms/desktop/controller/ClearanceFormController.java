package gov.brgy.ebms.desktop.controller;

import gov.brgy.ebms.desktop.api.dto.ClearanceDto;
import gov.brgy.ebms.desktop.api.dto.ClearanceRequestDto;
import gov.brgy.ebms.desktop.core.AsyncRunner;
import gov.brgy.ebms.desktop.core.Dialogs;
import gov.brgy.ebms.desktop.service.EbmsService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class ClearanceFormController {

    @FXML private TextField residentIdField;
    @FXML private TextField purposeField;
    @FXML private Label statusLabel;
    @FXML private Label controlNumberLabel;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private final EbmsService service = EbmsService.get();
    private ClearanceDto entity;
    private Runnable onSaved;

    public void setEntity(ClearanceDto dto) {
        this.entity = dto;
        if (dto != null) {
            if (residentIdField != null) { residentIdField.setText(String.valueOf(dto.residentId())); residentIdField.setEditable(false); }
            if (purposeField != null) { purposeField.setText(dto.purpose()); purposeField.setEditable(false); }
            if (statusLabel != null) statusLabel.setText("Status: " + dto.status());
            if (controlNumberLabel != null) controlNumberLabel.setText("Control #: " + dto.controlNumber());
            if (saveButton != null) saveButton.setVisible(false);
        }
    }

    public void setOnSaved(Runnable cb) { this.onSaved = cb; }

    @FXML
    public void handleSave() {
        if (entity != null) { close(); return; } // view only for existing
        String residentIdStr = residentIdField != null ? residentIdField.getText().trim() : "";
        String purpose = purposeField != null ? purposeField.getText().trim() : "";
        if (residentIdStr.isEmpty() || purpose.isEmpty()) {
            Dialogs.error("Resident ID and purpose are required.");
            return;
        }
        Long residentId;
        try { residentId = Long.parseLong(residentIdStr); }
        catch (NumberFormatException e) { Dialogs.error("Resident ID must be a number."); return; }

        if (saveButton != null) saveButton.setDisable(true);
        final Long rid = residentId;
        AsyncRunner.run(
            () -> service.submitClearance(new ClearanceRequestDto(rid, purpose)),
            r -> { if (saveButton != null) saveButton.setDisable(false); if (onSaved != null) onSaved.run(); close(); },
            t -> { if (saveButton != null) saveButton.setDisable(false); Dialogs.handleApiError(t); }
        );
    }

    @FXML
    public void handleCancel() { close(); }

    private void close() {
        if (saveButton != null && saveButton.getScene() != null)
            ((Stage) saveButton.getScene().getWindow()).close();
        else if (cancelButton != null && cancelButton.getScene() != null)
            ((Stage) cancelButton.getScene().getWindow()).close();
    }
}
