package gov.brgy.ebms.desktop.controller;

import gov.brgy.ebms.desktop.api.dto.ComplaintDto;
import gov.brgy.ebms.desktop.api.dto.ComplaintFilingRequest;
import gov.brgy.ebms.desktop.api.dto.PartyDto;
import gov.brgy.ebms.desktop.core.AsyncRunner;
import gov.brgy.ebms.desktop.core.Dialogs;
import gov.brgy.ebms.desktop.service.EbmsService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class ComplaintFormController {

    @FXML private TextField titleField;
    @FXML private TextArea narrativeArea;
    @FXML private TextField complainantNameField;
    @FXML private TextField respondentNameField;
    @FXML private Label statusLabel;
    @FXML private Label caseNumberLabel;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private final EbmsService service = EbmsService.get();
    private ComplaintDto entity;
    private Runnable onSaved;

    public void setEntity(ComplaintDto dto) {
        this.entity = dto;
        if (dto != null) {
            if (titleField != null) { titleField.setText(dto.title()); titleField.setEditable(false); }
            if (narrativeArea != null) { narrativeArea.setText(dto.narrative()); narrativeArea.setEditable(false); }
            if (statusLabel != null) statusLabel.setText("Status: " + dto.status());
            if (caseNumberLabel != null) caseNumberLabel.setText("Case #: " + dto.caseNumber());
            if (complainantNameField != null) complainantNameField.setEditable(false);
            if (respondentNameField != null) respondentNameField.setEditable(false);
            if (saveButton != null) saveButton.setVisible(false);
        }
    }

    public void setOnSaved(Runnable cb) { this.onSaved = cb; }

    @FXML
    public void handleSave() {
        if (entity != null) { close(); return; }
        String title = titleField != null ? titleField.getText().trim() : "";
        String narrative = narrativeArea != null ? narrativeArea.getText().trim() : "";
        if (title.isEmpty() || narrative.isEmpty()) {
            Dialogs.error("Title and narrative are required.");
            return;
        }

        List<PartyDto> parties = new ArrayList<>();
        if (complainantNameField != null && !complainantNameField.getText().isBlank()) {
            parties.add(new PartyDto(null, complainantNameField.getText().trim(), "COMPLAINANT"));
        }
        if (respondentNameField != null && !respondentNameField.getText().isBlank()) {
            parties.add(new PartyDto(null, respondentNameField.getText().trim(), "RESPONDENT"));
        }
        if (parties.isEmpty()) {
            Dialogs.error("At least one party (complainant or respondent) is required.");
            return;
        }

        if (saveButton != null) saveButton.setDisable(true);
        AsyncRunner.run(
            () -> service.fileComplaint(new ComplaintFilingRequest(title, narrative, parties)),
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
}
