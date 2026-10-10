package client.controller;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;

import java.net.URL;

public class AlertController {

    public AlertController() {
    }

    public static void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStyleClass().add("my-alert");

        URL cssFile = AlertController.class.getResource("/client/view/main.css");
        if (cssFile != null) {
            dialogPane.getStylesheets().add(cssFile.toExternalForm());
        } else {
            System.err.println("Could not find CSS file: main.css for dialog invite");
        }

        ButtonType btnOk = new ButtonType("Ok", ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(btnOk);

        // show alert
        alert.showAndWait();
    }

}
