package gov.brgy.ebms.desktop;

import gov.brgy.ebms.desktop.core.Config;
import gov.brgy.ebms.desktop.core.I18n;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.ResourceBundle;

public class DesktopApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        ResourceBundle bundle = ResourceBundle.getBundle("i18n/ui", I18n.currentLocale());
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/gov/brgy/ebms/desktop/fxml/login.fxml"),
            bundle
        );
        Parent root = loader.load();
        Scene scene = new Scene(root, 480, 360);
        primaryStage.setTitle(I18n.get("app.name"));
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        // Disable hostname verification for dev self-signed certificates (guarded by flag)
        if (Config.TRUST_ALL_CERTS) {
            System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
        }
        launch(args);
    }
}
