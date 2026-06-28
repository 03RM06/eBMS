package gov.brgy.ebms.desktop.controller;

import gov.brgy.ebms.desktop.api.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class DashboardController {

    @FXML private Label welcomeLabel;
    @FXML private Label rolesLabel;

    public void initialize(ApiClient.LoginResult loginResult, ApiClient apiClient) {
        welcomeLabel.setText("Welcome, " + loginResult.fullName() + "!");
        rolesLabel.setText("Roles: " + String.join(", ", loginResult.roles()));
    }

    public void initialize() {
        // Default FXML initialize — replaced by the overloaded version after login
    }
}
