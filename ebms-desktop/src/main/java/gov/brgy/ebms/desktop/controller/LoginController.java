package gov.brgy.ebms.desktop.controller;

import gov.brgy.ebms.desktop.api.dto.LoginResponse;
import gov.brgy.ebms.desktop.core.AsyncRunner;
import gov.brgy.ebms.desktop.core.Dialogs;
import gov.brgy.ebms.desktop.core.I18n;
import gov.brgy.ebms.desktop.core.Session;
import gov.brgy.ebms.desktop.service.EbmsService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.ResourceBundle;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label errorLabel;

    private final EbmsService service = EbmsService.get();

    @FXML
    public void initialize() {
        if (errorLabel != null) errorLabel.setVisible(false);
    }

    @FXML
    public void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Username and password are required.");
            return;
        }

        loginButton.setDisable(true);
        if (errorLabel != null) errorLabel.setVisible(false);

        AsyncRunner.run(
            () -> service.login(username, password),
            loginResponse -> {
                loginButton.setDisable(false);
                handleLoginSuccess(loginResponse);
            },
            t -> {
                loginButton.setDisable(false);
                showError(Dialogs.friendlyMessage(t));
            }
        );
    }

    private void handleLoginSuccess(LoginResponse lr) {
        Session.get().update(
            lr.accessToken(), lr.refreshToken(),
            lr.userId(), lr.username(), lr.fullName(), lr.roles()
        );

        if (lr.requiresPasswordChange()) {
            openChangePassword();
        } else {
            openMainShell();
        }
    }

    private void openMainShell() {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n/ui", I18n.currentLocale());
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/gov/brgy/ebms/desktop/fxml/mainShell.fxml"),
                bundle);
            Parent root = loader.load();

            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setScene(new Scene(root, 1100, 700));
            stage.setTitle("eBMS");
            stage.setMaximized(true);
        } catch (Exception ex) {
            showError("Failed to open main shell: " + ex.getMessage());
        }
    }

    private void openChangePassword() {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n/ui", I18n.currentLocale());
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/gov/brgy/ebms/desktop/fxml/changePassword.fxml"),
                bundle);
            Parent root = loader.load();

            ChangePasswordController ctrl = loader.getController();
            ctrl.setOnSuccess(this::openMainShell);
            ctrl.setForced(true);

            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setScene(new Scene(root, 480, 380));
            stage.setTitle("Change Password");
        } catch (Exception ex) {
            showError("Failed to open change password: " + ex.getMessage());
        }
    }

    private void showError(String message) {
        if (errorLabel != null) {
            errorLabel.setText(message);
            errorLabel.setVisible(true);
        } else {
            Dialogs.error(message);
        }
    }
}
