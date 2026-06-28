package gov.brgy.ebms.desktop.core;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

import java.util.ResourceBundle;

public class Navigator {

    private static StackPane centerHost;

    private Navigator() {}

    public static void setCenterHost(StackPane pane) {
        centerHost = pane;
    }

    public static void navigateTo(String fxmlName) {
        try {
            FXMLLoader loader = new FXMLLoader(
                Navigator.class.getResource("/gov/brgy/ebms/desktop/fxml/" + fxmlName),
                ResourceBundle.getBundle("i18n/ui", I18n.currentLocale()));
            Node node = loader.load();
            Platform.runLater(() -> {
                centerHost.getChildren().setAll(node);
            });
        } catch (Exception e) {
            Dialogs.error("Navigation error: " + e.getMessage());
        }
    }

    public static <T> T navigateToAndGetController(String fxmlName) {
        try {
            FXMLLoader loader = new FXMLLoader(
                Navigator.class.getResource("/gov/brgy/ebms/desktop/fxml/" + fxmlName),
                ResourceBundle.getBundle("i18n/ui", I18n.currentLocale()));
            Node node = loader.load();
            Platform.runLater(() -> centerHost.getChildren().setAll(node));
            return loader.getController();
        } catch (Exception e) {
            Dialogs.error("Navigation error: " + e.getMessage());
            return null;
        }
    }
}
