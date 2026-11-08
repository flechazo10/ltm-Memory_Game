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
    @FXML private Label lblMyName;
    @FXML private Label lblMyScore;
    @FXML private Label lblSelection;
    @FXML private javafx.scene.layout.FlowPane playerCards;
    @FXML private Label lblNoPlayers;
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
        for (Player p : list) {
            if (userLogin != null && p.getId() == userLogin.getId()) {
                // Không hiện chính mình trong bảng; lấy điểm mới nhất của mình từ server để cập nhật box "Của tôi"
                userLogin.setTotalScore(p.getTotalScore());
                showMyInfo();
            } else {
                players.add(p);
            }
        }
        renderPlayerCards();
    }

    public void setUserLogin(Player userLogin) {
        this.userLogin = userLogin;
        lblWelcome.setText("Xin chào, " + userLogin.getUsername());
        if (lblSelection != null && app != null) {
            lblSelection.setText(ClientService.themeName(app.getSelectedTheme()) + " · " + ClientService.difficultyLabel(app.getSelectedDifficulty()));
        }
        showMyInfo();
        // danh sách có thể đã về trước khi biết mình là ai -> lọc lại
        if (players.removeIf(p -> p.getId() == userLogin.getId())) {
            renderPlayerCards();
        }
    }

    private void showMyInfo() {
        if (userLogin == null) return;
        if (lblMyName != null) lblMyName.setText(userLogin.getUsername());
        if (lblMyScore != null) lblMyScore.setText(formatScore(userLogin.getTotalScore()) + " điểm");
    }

    @FXML
    private void onChangeTheme() {
        if (app != null) app.showThemeSelectUI();
    }

    @FXML
    private void onBackToMenu() {
        if (app != null) app.showMenuUI();
    }

    @FXML
    private void onMyHistory() {
        if (app != null && userLogin != null) {
            app.openMyHistory(userLogin);
        }
    }

    @FXML
    private void onLogout() throws IOException {
        showLogoutModal();
    }

    @FXML
    public void onHistory() {
        if (app != null) {
            app.setListsOpenedFromMenu(false);
            app.sendMessage(new Message(MessageType.GET_HISTORY, null));
        }
    }

    @FXML
    private void onRanking() {
        if (app != null) {
            app.setListsOpenedFromMenu(false);
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
        renderPlayerCards();
    }

    // ===== Thẻ người chơi ở sảnh =====
    private static final String[] AVATAR_COLORS = {"#3b8fe8", "#4cbb3a", "#e84b3b", "#f5a623", "#9b59b6", "#16a085", "#e67e22", "#2c3e50"};

    private void renderPlayerCards() {
        if (playerCards == null) return;
        playerCards.getChildren().clear();
        for (Player p : players) {
            playerCards.getChildren().add(createPlayerCard(p));
        }
        lblNoPlayers.setVisible(players.isEmpty());
    }

    private javafx.scene.Node createPlayerCard(Player p) {
        String name = p.getUsername() == null ? "?" : p.getUsername();
        String status = p.getStatus() == null ? "" : p.getStatus().toUpperCase();
        boolean free = status.equals(String.valueOf(Status.ONLINE));
        boolean offline = status.equals(String.valueOf(Status.OFFLINE));

        // Avatar tròn: chữ cái đầu, màu cố định theo tên
        javafx.scene.shape.Circle circle = new javafx.scene.shape.Circle(30,
                javafx.scene.paint.Color.web(AVATAR_COLORS[Math.abs(name.hashCode()) % AVATAR_COLORS.length]));
        circle.setStroke(javafx.scene.paint.Color.web("#3a3a3a"));
        circle.setStrokeWidth(3);
        Label initial = new Label(name.substring(0, 1).toUpperCase());
        initial.getStyleClass().add("avatar-letter");
        javafx.scene.layout.StackPane avatar = new javafx.scene.layout.StackPane(circle, initial);

        Label lblName = new Label(name);
        lblName.getStyleClass().add("tile-name");
        Label lblScore = new Label(formatScore(p.getTotalScore()) + " điểm");
        lblScore.getStyleClass().add("tile-score");
        Label lblStatus = new Label(free ? "● Rảnh" : offline ? "● Offline" : "● Đang chơi");
        lblStatus.getStyleClass().addAll("status-pill", free ? "status-free" : offline ? "status-off" : "status-busy");

        Button btnInvite = new Button(free ? "Mời đấu" : "Đang bận");
        btnInvite.getStyleClass().addAll(free ? "btn-green" : "btn-neutral", "btn-small");
        btnInvite.setMaxWidth(Double.MAX_VALUE);
        btnInvite.setDisable(!free);
        btnInvite.setOnAction(e -> showInviteDialog(p));

        VBox card = new VBox(6, avatar, lblName, lblScore, lblStatus, btnInvite);
        card.setAlignment(Pos.TOP_CENTER);
        card.getStyleClass().add("player-tile");
        if (free) card.setOnMouseClicked(e -> { if (e.getClickCount() == 1 && !(e.getTarget() instanceof Button)) showInviteDialog(p); });
        return card;
    }

    private void showLogoutModal() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/view/LogoutModal.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);

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
            // Chủ đề + độ khó đã chọn từ trước (Menu -> chọn chủ đề), ở đây chỉ hỏi xác nhận
            String theme = app.getSelectedTheme();
            String difficulty = app.getSelectedDifficulty();
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Thách đấu");
            alert.setHeaderText("Mời " + player.getUsername() + " thi đấu?");
            alert.setContentText("Chủ đề: " + ClientService.themeName(theme) + "\nĐộ khó: " + ClientService.difficultyLabel(difficulty));

            DialogPane dialogPane = alert.getDialogPane();
            dialogPane.getStyleClass().add("my-alert");
            URL cssFile = getClass().getResource("/client/view/main.css");
            if (cssFile != null) {
                dialogPane.getStylesheets().add(cssFile.toExternalForm());
            }

            ButtonType btnOk = new ButtonType("Mời", ButtonBar.ButtonData.OK_DONE);   // nút chính -> xanh lá
            ButtonType btnCancel = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(btnOk, btnCancel);

            alert.showAndWait().ifPresent(result -> {
                if (result == btnOk) {
                    Object[] content = {userLogin, player, theme, difficulty};
                    app.sendMessage(new Message(MessageType.INVITE_REQUEST, content));
                }
            });
        }
    }

    public void showAcceptDialog(Player fromPlayer, String category, String difficulty) {
        app.showAcceptDialog(fromPlayer, category, difficulty);   // dùng chung hộp thoại ở ClientService
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
        if (btnMusic != null) {
            btnMusic.setText(isMusicOn ? "Nhạc: Bật" : "Nhạc: Tắt");
        }
    }

    private static String formatScore(double score) {
        return score == Math.floor(score) ? String.valueOf((long) score) : String.valueOf(score);
    }
}