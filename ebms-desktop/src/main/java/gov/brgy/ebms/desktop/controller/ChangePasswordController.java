package gov.brgy.ebms.desktop.controller;

import gov.brgy.ebms.desktop.api.dto.ChangePasswordRequest;
import gov.brgy.ebms.desktop.core.AsyncRunner;
import gov.brgy.ebms.desktop.core.Dialogs;
import gov.brgy.ebms.desktop.service.EbmsService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;

public class ChangePasswordController {

    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button saveButton;
    @FXML private Label errorLabel;
    @FXML private Label titleLabel;

    private final EbmsService service = EbmsService.get();
    private Runnable onSuccess;
    private boolean forced = false;

    @FXML
    public void initialize() {
        if (errorLabel != null) errorLabel.setVisible(false);
    }

    public void setOnSuccess(Runnable callback) {
        this.onSuccess = callback;
    }

    public void setForced(boolean forced) {
        this.forced = forced;
        if (forced && titleLabel != null) {
            titleLabel.setText("You must change your password before continuing.");
        }
    }

    @FXML
    public void handleSave() {
        String current = currentPasswordField.getText();
        String newPw = newPasswordField.getText();
        String confirm = confirmPasswordField.getText();

        if (current.isEmpty() || newPw.isEmpty() || confirm.isEmpty()) {
            showError("All fields are required.");
            return;
        }
        if (!newPw.equals(confirm)) {
            showError("New password and confirmation do not match.");
            return;
        }

        saveButton.setDisable(true);
        if (errorLabel != null) errorLabel.setVisible(false);

        AsyncRunner.runVoid(
            () -> service.changePassword(new ChangePasswordRequest(current, newPw, confirm)),
            () -> {
                saveButton.setDisable(false);
                Dialogs.info("Password changed successfully.");
                if (onSuccess != null) onSuccess.run();
            },
            t -> {
                saveButton.setDisable(false);
                showError("Failed: " + t.getMessage());
            }
        );
    }

    @FXML
    public void handleCancel() {
        if (!forced && onSuccess == null) {
            // Navigate back if not forced
            currentPasswordField.getScene().getWindow().hide();
        }
    }

    private void showError(String msg) {
        if (errorLabel != null) {
            errorLabel.setText(msg);
            errorLabel.setVisible(true);
        } else {
            Dialogs.error(msg);
        }
    }
}
