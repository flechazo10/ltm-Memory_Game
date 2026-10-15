package client;

import client.service.ClientService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * Điểm khởi động phía Client.
 * Địa chỉ server mặc định localhost:23456 (trùng ServerRun.PORT).
 * Có thể đổi khi chạy: -Dserver.host=192.168.1.10 -Dserver.port=23456
 */
public class ClientRunApp extends Application {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 23456;

    private ClientService clientService;

    @Override
    public void start(Stage primaryStage) {
        String host = System.getProperty("server.host", DEFAULT_HOST);
        int port = Integer.getInteger("server.port", DEFAULT_PORT);

        primaryStage.setTitle("Lật Bài Online");
        primaryStage.setMinWidth(960);
        primaryStage.setMinHeight(640);

        clientService = new ClientService(primaryStage);
        clientService.showLoginUI();

        try {
            clientService.connect(host, port);
        } catch (Exception e) {
            // Vẫn cho hiện màn đăng nhập; khi bấm Đăng nhập sẽ tự thử kết nối lại.
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Chưa kết nối được máy chủ");
            alert.setHeaderText("Không kết nối được tới " + host + ":" + port);
            alert.setContentText("Hãy bật Server (chạy server.ServerRun) rồi bấm Đăng nhập để thử lại.");
            alert.show();
        }
    }

    @Override
    public void stop() {
        if (clientService != null) {
            clientService.close();
        }
        Platform.exit();
        System.exit(0);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
