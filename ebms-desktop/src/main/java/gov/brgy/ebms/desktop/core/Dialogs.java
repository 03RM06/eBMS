package gov.brgy.ebms.desktop.core;

import gov.brgy.ebms.desktop.api.ApiException;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.stream.Collectors;

public class Dialogs {

    private Dialogs() {}

    public static void error(String msg) {
        alert(Alert.AlertType.ERROR, "Error", msg);
    }

    public static void info(String msg) {
        alert(Alert.AlertType.INFORMATION, "Info", msg);
    }

    public static boolean confirm(String msg) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.OK, ButtonType.CANCEL);
        return a.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    public static void handleApiError(Throwable t) {
        if (t instanceof ApiException ae) {
            if (ae.getFieldErrors() != null && !ae.getFieldErrors().isEmpty()) {
                String errors = ae.getFieldErrors().values().stream()
                    .collect(Collectors.joining(", "));
                error("Validation failed: " + errors);
            } else {
                error(ae.getMessage() != null ? ae.getMessage() : "An error occurred (status " + ae.getStatus() + ")");
            }
        } else {
            error(t != null ? t.getMessage() : "Unknown error");
        }
    }

    private static void alert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setContentText(msg);
        a.showAndWait();
    }
}
