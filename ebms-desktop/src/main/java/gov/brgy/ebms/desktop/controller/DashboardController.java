package gov.brgy.ebms.desktop.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Legacy dashboard controller — retained for dashboard.fxml compatibility.
 * The main shell (MainShellController) is the primary navigation host in the new architecture.
 */
public class DashboardController {

    @FXML private Label welcomeLabel;
    @FXML private Label rolesLabel;

    @FXML
    public void initialize() {
        // Placeholder — populated programmatically if still used
    }
}
