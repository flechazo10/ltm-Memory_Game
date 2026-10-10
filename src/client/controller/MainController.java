package client.controller;

import client.service.ClientService;
import constants.MessageType;
import constants.Status;
import entity.Message;
import entity.Player;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.*;
import java.net.URL;
import java.util.*;

public class MainController {
    private ClientService app;
    private Player userLogin;
    private boolean isMusicOn;

    @FXML private Label lblWelcome;
    @FXML private TableView<Player> tblPlayers;
    @FXML private TableColumn<Player, String> colName;
    @FXML private TableColumn<Player, Double> colScore; // Đổi sang Double
    @FXML private TableColumn<Player, String> colStatus;
    @FXML private Button btnMusic;

    private ObservableList<Player> players = FXCollections.observableArrayList();

    public void setClient(ClientService app) throws IOException {
        this.app = app;
        this.isMusicOn = app.isMusicOn();
        loadUsers();
        loadMusic();
    }

    private void loadUsers() {
        try {
            app.sendMessage(new Message(MessageType.GET_PLAYERS, null));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updatePlayersList(List<Player> list) {
        players.clear();
        players.addAll(list);
        tblPlayers.setItems(players);
    }

    public void setUserLogin(Player userLogin) {
        this.userLogin = userLogin;
        lblWelcome.setText("Welcome, " + userLogin.getUsername());
    }

    @FXML
    private void onLogout() throws IOException {
        showLogoutModal();
    }

    @FXML
    public void onHistory() {
        if (app != null) {
            app.sendMessage(new Message(MessageType.GET_HISTORY, null));
        }
    }

    @FXML
    private void onRanking() {
        if (app != null) {
            app.sendMessage(new Message(MessageType.GET_RANKING, null));
        }
    }

    @FXML
    private void onRefresh() {
        loadUsers();
    }

    @FXML
    private void onMusic() {
        isMusicOn = !isMusicOn;
        loadMusic();
        app.setMusicOn(isMusicOn);
    }

    @FXML
    private void initialize() {
        colName.setCellValueFactory(new PropertyValueFactory<>("username"));
        colScore.setCellValueFactory(new PropertyValueFactory<>("totalScore"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        colStatus.setCellFactory(column -> new TableCell<Player, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                } else {
                    status = status.toUpperCase();
                    if (status.equals(String.valueOf(Status.ONLINE))) {
                        setText("Online");
                        setStyle("-fx-text-fill: #00FF99; -fx-font-weight: bold;");
                    } else if (status.equals(String.valueOf(Status.OFFLINE))) {
                        setText("Offline");
                        setStyle("-fx-text-fill: #AAAAAA; -fx-font-weight: normal;");
                    } else {
                        setText("In Game");
                        setStyle("-fx-text-fill: #efd967; -fx-font-weight: bold;");
                    }
                    setAlignment(Pos.CENTER);
                }
            }
        });

        // Click 1 LẦN vào đối thủ để hiện popup thách đấu
        tblPlayers.setRowFactory(tv -> {
            TableRow<Player> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getClickCount() == 1) {
                    Player clickedPlayer = row.getItem();
                    showInviteDialog(clickedPlayer);
                }
            });
            return row;
        });
    }

    private void showLogoutModal() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/view/LogoutModal.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            scene.setFill(Color.ALICEBLUE);

            LogoutController logoutController = loader.getController();
            Stage modalStage = new Stage();
            modalStage.initStyle(StageStyle.TRANSPARENT);

            URL cssLocation = getClass().getResource("/client/view/auth.css");
            if (cssLocation != null) {
                scene.getStylesheets().add(cssLocation.toExternalForm());
            }

            logoutController.setModalStage(modalStage);
            logoutController.setMainController(this);
            logoutController.setCurrentPlayer(userLogin);
            logoutController.setClientService(app);

            modalStage.setTitle("Logout");
            modalStage.setScene(scene);
            modalStage.initModality(Modality.APPLICATION_MODAL);
            modalStage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showInviteDialog(Player player) {
        if (player == null || userLogin == null) return;
        String status = player.getStatus() != null ? player.getStatus().toUpperCase() : "";
        if (status.equals(String.valueOf(Status.ONLINE)) && !player.getUsername().equals(userLogin.getUsername())) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Thách đấu");
            alert.setHeaderText("Bạn có muốn thách đấu với " + player.getUsername() + "?");

            String[] categories = {"badminton", "footballer", "kimesu", "onepunchman", "sakamoto"};
            ChoiceBox<String> choiceBox = new ChoiceBox<>(FXCollections.observableArrayList(categories));
            choiceBox.getSelectionModel().selectFirst();
            choiceBox.setMaxWidth(Double.MAX_VALUE);
            VBox.setVgrow(choiceBox, Priority.ALWAYS);

            Label label = new Label("Chọn chủ đề: ");
            VBox boxContent = new VBox(10);
            boxContent.getChildren().addAll(label, choiceBox);
            alert.getDialogPane().setContent(boxContent);

            DialogPane dialogPane = alert.getDialogPane();
            dialogPane.getStyleClass().add("my-alert");

            URL cssFile = getClass().getResource("/client/view/main.css");
            if (cssFile != null) {
                dialogPane.getStylesheets().add(cssFile.toExternalForm());
            }

            ButtonType btnOk = new ButtonType("Đồng ý");
            ButtonType btnCancel = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(btnOk, btnCancel);

            alert.showAndWait().ifPresent(result -> {
                if (result == btnOk) {
                    try {
                        String selected = choiceBox.getValue();
                        Object[] content = {userLogin, player, selected};
                        app.sendMessage(new Message(MessageType.INVITE_REQUEST, content));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    public void showAcceptDialog(Player fromPlayer, String category) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Lời mời thách đấu");
        alert.setHeaderText(fromPlayer.getUsername() + " mời bạn thi đấu chủ đề " + category);
        alert.setContentText("Bạn có chấp nhận không?");

        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStyleClass().add("my-alert");

        URL cssFile = getClass().getResource("/client/view/main.css");
        if (cssFile != null) {
            dialogPane.getStylesheets().add(cssFile.toExternalForm());
        }

        ButtonType btnOk = new ButtonType("Chấp nhận");
        ButtonType btnCancel = new ButtonType("Từ chối", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnOk, btnCancel);

        alert.showAndWait().ifPresent(result -> {
            boolean accepted = (result == btnOk);
            try {
                Object[] content = {userLogin, fromPlayer, accepted, category};
                app.sendMessage(new Message(MessageType.INVITE_RESPONSE, content));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStyleClass().add("my-alert");

        URL cssFile = getClass().getResource("/client/view/main.css");
        if (cssFile != null) {
            dialogPane.getStylesheets().add(cssFile.toExternalForm());
        }

        ButtonType btnOk = new ButtonType("Ok", ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(btnOk);
        alert.showAndWait();
    }

    private void loadMusic() {
        String path = getClass().getResource(isMusicOn ? "/assets/images/background/btn_on_music.png" : "/assets/images/background/btn_off_music.png").toString();
        btnMusic.setStyle("-fx-background-image: url('" + path + "'); -fx-background-size: cover; -fx-background-radius: 50%; -fx-background-repeat: no-repeat; -fx-background-position: center;");
    }
}