package gov.brgy.ebms.desktop.controller;

import gov.brgy.ebms.desktop.core.AsyncRunner;
import gov.brgy.ebms.desktop.core.Dialogs;
import gov.brgy.ebms.desktop.core.I18n;
import gov.brgy.ebms.desktop.core.Navigator;
import gov.brgy.ebms.desktop.core.Session;
import gov.brgy.ebms.desktop.service.EbmsService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.util.ResourceBundle;

public class MainShellController {

    @FXML private Button btnResidents;
    @FXML private Button btnHouseholds;
    @FXML private Button btnClearances;
    @FXML private Button btnComplaints;
    @FXML private Button btnFees;
    @FXML private Button btnAudit;
    @FXML private Button btnLogout;
    @FXML private Label lblBarangayName;
    @FXML private Label lblUser;
    @FXML private ToggleButton toggleLang;
    @FXML private StackPane centerHost;

    private final EbmsService service = EbmsService.get();

    @FXML
    public void initialize() {
        Navigator.setCenterHost(centerHost);

        // Set user display name
        if (lblUser != null) {
            lblUser.setText(Session.get().getFullName());
        }

        // Role-based: hide audit unless SUPER_ADMIN or BARANGAY_CAPTAIN
        if (btnAudit != null) {
            boolean canSeeAudit = Session.get().hasAnyRole("SUPER_ADMIN", "BARANGAY_CAPTAIN");
            btnAudit.setVisible(canSeeAudit);
            btnAudit.setManaged(canSeeAudit);
        }

        // Set locale toggle label
        if (toggleLang != null) {
            boolean isFil = "fil".equals(Session.get().getLocale());
            toggleLang.setSelected(isFil);
            toggleLang.setText(isFil ? "EN" : "FIL");
        }

        // Fetch barangay name in background
        AsyncRunner.run(
            () -> service.getPublicConfig(),
            config -> {
                if (config != null && lblBarangayName != null) {
                    lblBarangayName.setText(config.getOrDefault("barangayName", "eBMS"));
                }
            },
            t -> {
                if (lblBarangayName != null) lblBarangayName.setText("eBMS");
            }
        );

        // Start on residents screen
        Navigator.navigateTo("residentList.fxml");
    }

    @FXML
    public void onResidents() {
        Navigator.navigateTo("residentList.fxml");
    }

    @FXML
    public void onHouseholds() {
        Navigator.navigateTo("householdList.fxml");
    }

    @FXML
    public void onClearances() {
        Navigator.navigateTo("clearanceList.fxml");
    }

    @FXML
    public void onComplaints() {
        Navigator.navigateTo("complaintList.fxml");
    }

    @FXML
    public void onFees() {
        Navigator.navigateTo("feeList.fxml");
    }

    @FXML
    public void onAudit() {
        Navigator.navigateTo("audit.fxml");
    }

    @FXML
    public void onToggleLang() {
        boolean nowFil = toggleLang.isSelected();
        String lang = nowFil ? "fil" : "en";
        toggleLang.setText(nowFil ? "EN" : "FIL");
        I18n.setLocale(lang);
        AsyncRunner.runVoid(
            () -> service.updateLocale(lang),
            () -> {},
            t -> Dialogs.handleApiError(t)
        );
    }

    @FXML
    public void onLogout() {
        if (!Dialogs.confirm("Are you sure you want to logout?")) return;
        AsyncRunner.runVoid(
            () -> service.logout(),
            this::navigateToLogin,
            t -> navigateToLogin()
        );
    }

    private void navigateToLogin() {
        Session.get().clear();
        try {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n/ui", I18n.currentLocale());
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/gov/brgy/ebms/desktop/fxml/login.fxml"),
                bundle);
            Parent root = loader.load();
            Stage stage = (Stage) btnLogout.getScene().getWindow();
            stage.setScene(new Scene(root, 480, 360));
            stage.setMaximized(false);
            stage.setTitle("eBMS Login");
        } catch (Exception ex) {
            Dialogs.error("Failed to navigate to login: " + ex.getMessage());
        }
    }
}
