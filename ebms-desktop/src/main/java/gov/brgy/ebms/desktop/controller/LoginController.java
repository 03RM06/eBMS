package gov.brgy.ebms.desktop.controller;

import gov.brgy.ebms.desktop.api.ApiClient;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
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

    private final ApiClient apiClient = new ApiClient();

    @FXML
    public void initialize() {
        errorLabel.setVisible(false);
    }

    @FXML
    public void handleLogin(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Username and password are required.");
            return;
        }

        loginButton.setDisable(true);
        errorLabel.setVisible(false);

        Task<ApiClient.LoginResult> task = new Task<>() {
            @Override
            protected ApiClient.LoginResult call() throws Exception {
                return apiClient.login(username, password);
            }
        };

        task.setOnSucceeded(e -> {
            loginButton.setDisable(false);
            openDashboard(task.getValue());
        });

        task.setOnFailed(e -> {
            loginButton.setDisable(false);
            showError("Login failed: " + task.getException().getMessage());
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void openDashboard(ApiClient.LoginResult loginResult) {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n/ui_en");
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/gov/brgy/ebms/desktop/fxml/dashboard.fxml"),
                bundle
            );
            Parent root = loader.load();

            DashboardController dashboardController = loader.getController();
            dashboardController.initialize(loginResult, apiClient);

            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setScene(new Scene(root, 900, 600));
            stage.setTitle(bundle.getString("app.name") + " - Dashboard");
        } catch (Exception ex) {
            showError("Failed to open dashboard: " + ex.getMessage());
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
