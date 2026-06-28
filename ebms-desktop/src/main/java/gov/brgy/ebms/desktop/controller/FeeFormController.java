package gov.brgy.ebms.desktop.controller;

import gov.brgy.ebms.desktop.api.dto.FeeDto;
import gov.brgy.ebms.desktop.api.dto.FeeRequest;
import gov.brgy.ebms.desktop.core.AsyncRunner;
import gov.brgy.ebms.desktop.core.Dialogs;
import gov.brgy.ebms.desktop.service.EbmsService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.math.BigDecimal;

public class FeeFormController {

    @FXML private TextField clearanceIdField;
    @FXML private TextField feeTypeField;
    @FXML private TextField amountField;
    @FXML private Label statusLabel;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private final EbmsService service = EbmsService.get();
    private FeeDto entity;
    private Runnable onSaved;

    public void setEntity(FeeDto dto) {
        this.entity = dto;
        if (dto != null) {
            if (clearanceIdField != null) { clearanceIdField.setText(String.valueOf(dto.clearanceId())); clearanceIdField.setEditable(false); }
            if (feeTypeField != null) { feeTypeField.setText(nullToEmpty(dto.feeType())); feeTypeField.setEditable(false); }
            if (amountField != null) { amountField.setText(dto.amount() != null ? dto.amount().toString() : ""); amountField.setEditable(false); }
            if (statusLabel != null) statusLabel.setText("Status: " + dto.status());
            if (saveButton != null) saveButton.setVisible(false);
        }
    }

    public void setOnSaved(Runnable cb) { this.onSaved = cb; }

    @FXML
    public void handleSave() {
        if (entity != null) { close(); return; }
        String clearanceIdStr = clearanceIdField != null ? clearanceIdField.getText().trim() : "";
        String feeType = feeTypeField != null ? feeTypeField.getText().trim() : "";
        String amountStr = amountField != null ? amountField.getText().trim() : "";

        if (clearanceIdStr.isEmpty() || amountStr.isEmpty()) {
            Dialogs.error("Clearance ID and amount are required.");
            return;
        }
        Long clearanceId;
        BigDecimal amount;
        try { clearanceId = Long.parseLong(clearanceIdStr); }
        catch (NumberFormatException e) { Dialogs.error("Clearance ID must be a number."); return; }
        try { amount = new BigDecimal(amountStr); }
        catch (NumberFormatException e) { Dialogs.error("Amount must be a valid number."); return; }

        if (saveButton != null) saveButton.setDisable(true);
        final Long cid = clearanceId;
        final BigDecimal amt = amount;
        final String ft = feeType.isBlank() ? null : feeType;
        AsyncRunner.run(
            () -> service.createFee(new FeeRequest(cid, ft, amt)),
            r -> { if (saveButton != null) saveButton.setDisable(false); if (onSaved != null) onSaved.run(); close(); },
            t -> { if (saveButton != null) saveButton.setDisable(false); Dialogs.handleApiError(t); }
        );
    }

    @FXML
    public void handleCancel() { close(); }

    private void close() {
        Stage stage = null;
        if (saveButton != null && saveButton.getScene() != null)
            stage = (Stage) saveButton.getScene().getWindow();
        else if (cancelButton != null && cancelButton.getScene() != null)
            stage = (Stage) cancelButton.getScene().getWindow();
        if (stage != null) stage.close();
    }

    private static String nullToEmpty(String s) { return s != null ? s : ""; }
}
