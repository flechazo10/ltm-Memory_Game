package client.controller;

import client.service.ClientService;
import constants.MessageType;
import entity.Message;
import entity.Player;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.net.URL;
import java.util.Arrays;

/** Menu chính sau khi đăng nhập. */
public class MenuController {
    private ClientService app;

    @FXML private Label lblHello;
    @FXML private Label lblDifficulty;
    @FXML private Label lblSound;

    public void setClient(ClientService app) {
        this.app = app;
        Player me = app.getCurrentPlayer();
        if (me != null) lblHello.setText("Xin chào, " + me.getUsername());
        refresh();
    }

    private void refresh() {
        lblDifficulty.setText(ClientService.difficultyLabel(app.getSelectedDifficulty()));
        lblSound.setText(app.isMusicOn() ? "Bật" : "Tắt");
    }

    @FXML
    private void onPlay() {
        app.showThemeSelectUI();
    }

    /** Bấm để đổi vòng: Dễ -> Vừa -> Khó -> Dễ ... */
    @FXML
    private void onDifficulty() {
        String[] all = ClientService.DIFFICULTIES;
        int i = Arrays.asList(all).indexOf(app.getSelectedDifficulty());
        app.setSelectedDifficulty(all[(i + 1) % all.length]);
        refresh();
    }

    /** Bật/tắt cả nhạc nền lẫn hiệu ứng lật bài. */
    @FXML
    private void onSound() {
        app.setMusicOn(!app.isMusicOn());
        refresh();
    }

    @FXML
    private void onRanking() {
        app.setListsOpenedFromMenu(true);
        app.sendMessage(new Message(MessageType.GET_RANKING, null));
    }

    @FXML
    private void onHistory() {
        app.setListsOpenedFromMenu(true);
        app.sendMessage(new Message(MessageType.GET_HISTORY, null));
    }

    @FXML
    private void onGuide() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Hướng dẫn");
        alert.setHeaderText("LUẬT CHƠI");
        Label content = new Label(
                "• Chọn độ khó ở Menu: Dễ 2×4 (4 cặp), Vừa 4×4 (8 cặp), Khó 4×6 (12 cặp).\n"
              + "• PLAY → chọn chủ đề → Vào sảnh → bấm vào người đang Rảnh để mời.\n"
              + "• Hai người lần lượt lật 2 lá, mỗi lượt 15 giây. Trùng cặp được +1 cặp và lật tiếp, sai thì đổi lượt.\n"
              + "• Hết bài hoặc hết 15 phút: ai nhiều cặp hơn thắng.\n"
              + "• Điểm xếp hạng: thắng +1, hòa +0.5, thua 0.\n"
              + "• Trong trận có thể Cầu hòa (tối đa 3 lần) hoặc Xin thua.\n"
              + "• Hết giờ 3 lượt liên tiếp hoặc rời trận giữa chừng sẽ bị xử thua.");
        content.setWrapText(true);
        content.setLineSpacing(4);
        content.setMaxWidth(470);
        alert.getDialogPane().setContent(content);
        style(alert);
        alert.getDialogPane().setPrefWidth(520);
        alert.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        alert.showAndWait();
    }

    @FXML
    private void onLogout() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Đăng xuất");
        alert.setHeaderText("Đăng xuất?");
        alert.setContentText("Bạn sẽ quay về màn hình đăng nhập.");
        ButtonType ok = new ButtonType("Đăng xuất", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(cancel, ok);
        style(alert);
        alert.showAndWait().ifPresent(r -> {
            if (r == ok) app.sendMessage(new Message(MessageType.LOGOUT, app.getCurrentPlayer()));
        });
    }

    private void style(Alert alert) {
        DialogPane pane = alert.getDialogPane();
        pane.getStyleClass().add("my-alert");
        URL css = getClass().getResource("/client/view/main.css");
        if (css != null) pane.getStylesheets().add(css.toExternalForm());
    }
}
