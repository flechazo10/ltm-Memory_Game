package client.service;

import client.ClientRunApp;
import client.controller.*;
import constants.MessageType;
import entity.DetailMatch;
import entity.Message;
import entity.Player;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ClientService {
    private final Stage primaryStage;
    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;

    private MainController mainController;
    private LoginController loginController;
    private RankingController rankingController;
    private HistoryController historyController;
    private HistoryDetailController historyDetailController;
    private GameRoomController gameRoomController;
    private SignUpController signUpController;

    private List<Player> cachedHistoryList;
    private String selectedPlayerName;
    private boolean historyDetailFromLobby = false;   // vào màn chi tiết lịch sử từ box "Của tôi" ở sảnh
    private boolean listsOpenedFromMenu = false;      // Bảng xếp hạng / Lịch sử mở từ Menu chính (quay lại Menu) hay từ sảnh

    // ===== Chủ đề + độ khó người chơi chọn ở Menu / màn chọn chủ đề =====
    /** Mã chủ đề (tên thư mục trong assets/images) và tên hiển thị. Thêm chủ đề: thêm 1 dòng ở đây. */
    public static final String[][] THEMES = {
            {"giang_ho_mang", "Giang Hồ Mạng"},
            {"messi", "Goat"},
            {"truong_con_nam_dinh", "Trường Con Nam Định"},
    };
    public static final String[] DIFFICULTIES = {"easy", "medium", "hard"};
    private String selectedTheme = THEMES[0][0];
    private String selectedDifficulty = "hard";

    public static String themeName(String key) {
        for (String[] t : THEMES) if (t[0].equals(key)) return t[1];
        return key;
    }
    public static String difficultyLabel(String d) {
        if ("easy".equals(d)) return "Dễ (2x4)";
        if ("medium".equals(d)) return "Vừa (4x4)";
        return "Khó (4x6)";
    }
    public String getSelectedTheme() { return selectedTheme; }
    public void setSelectedTheme(String theme) { this.selectedTheme = theme; }
    public String getSelectedDifficulty() { return selectedDifficulty; }
    public void setSelectedDifficulty(String d) { this.selectedDifficulty = d; }
    public void setListsOpenedFromMenu(boolean fromMenu) { this.listsOpenedFromMenu = fromMenu; }

    /** Nút quay lại ở Bảng xếp hạng / danh sách Lịch sử. */
    public void backFromLists() {
        if (listsOpenedFromMenu) showMenuUI(); else showMainUI();
    }

    private volatile boolean isConnected = false;
    private String serverHost;
    private int serverPort;
    private Player currentPlayer;
    private MediaPlayer mediaPlayer;
    private boolean musicOn = true;
    private static final String MUSIC_PATH = "/assets/sound/sound_ltm.mp3";

    public ClientService(Stage primaryStage) {
        this.primaryStage = primaryStage;
        // Nạp font game (dùng trong CSS với tên "LatBai"); thiếu file thì dùng font hệ thống
        URL font = getClass().getResource("/assets/fonts/LatBai-Baloo2Bold.ttf");
        if (font != null) javafx.scene.text.Font.loadFont(font.toExternalForm(), 14);
        // Icon cửa sổ / thanh taskbar = logo game
        URL logo = getClass().getResource("/assets/images/logo.png");
        if (logo != null) primaryStage.getIcons().add(new javafx.scene.image.Image(logo.toExternalForm()));
    }

    public void setSelectedPlayerName(String name) {
        this.selectedPlayerName = name;
        this.historyDetailFromLobby = false;   // mặc định: vào từ danh sách Lịch sử đấu
    }

    /** Nút "Lịch sử đấu của tôi" ở sảnh: mở thẳng chi tiết lịch sử của mình. */
    public void openMyHistory(Player me) {
        this.selectedPlayerName = me.getUsername();
        this.historyDetailFromLobby = true;
        sendMessage(new Message(MessageType.GET_HISTORY_DETAIL, me.getId()));
    }

    /** Nút "← Quay lại" ở màn chi tiết lịch sử: về đúng màn đã mở nó. */
    public void backFromHistoryDetail() {
        if (historyDetailFromLobby || cachedHistoryList == null) {
            showMainUI();
        } else {
            showHistoryUI(cachedHistoryList);
        }
    }

    public String getSelectedPlayerName() {
        return this.selectedPlayerName;
    }

    public int getOutStreamHash() {
        return (out == null ? -1 : out.hashCode());
    }

    public List<Player> getCachedHistoryList() {
        return cachedHistoryList;
    }

    public void connect(String host, int port) throws IOException {
        this.serverHost = host;
        this.serverPort = port;
        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
        isConnected = true;

        System.out.println("Đã kết nối tới server: " + host + ":" + port);
        listenServer();
    }

    public void sendMessage(Message msg) {
        if (!isConnected && !tryReconnect()) {
            Platform.runLater(() -> AlertController.showAlert("Chưa kết nối",
                    "Không kết nối được tới máy chủ. Hãy kiểm tra Server đã bật chưa rồi thử lại."));
            return;
        }
        try {
            synchronized (this) {
                out.writeObject(msg);
                out.reset();
                out.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Thử kết nối lại tới server đã dùng lần trước (vd: lúc mở app server chưa bật). */
    private boolean tryReconnect() {
        if (serverHost == null) return false;
        try {
            connect(serverHost, serverPort);
            return true;
        } catch (IOException e) {
            System.err.println("Kết nối lại thất bại: " + e.getMessage());
            return false;
        }
    }

    private void listenServer() {
        Thread listener = new Thread(() -> {
            try {
                while (isConnected) {
                    Message msg = (Message) in.readObject();
                    if (msg == null) continue;

                    switch (msg.getType()) {
                        case LOGIN_SUCCESS:
                            currentPlayer = (Player) msg.getContent();
                            setupMusic();
                            Platform.runLater(this::showMenuUI);   // đăng nhập xong -> Menu chính
                            break;

                        case LOGIN_FAILURE:
                            String err = (String) msg.getContent();
                            Platform.runLater(() -> AlertController.showAlert("Đăng nhập thất bại", err));
                            break;

                        case GET_PLAYERS:
                            List<Player> players = (List<Player>) msg.getContent();
                            Platform.runLater(() -> {
                                if (mainController != null) {
                                    mainController.updatePlayersList(players);
                                }
                            });
                            break;

                        case STATUS_UPDATE:
                            String statusMsg = (String) msg.getContent();
                            System.out.println("Status update: " + statusMsg);
                            sendMessage(new Message(MessageType.GET_PLAYERS, null));
                            break;

                        case INVITE_INCOMING:
                            Object[] inviteContent = (Object[]) msg.getContent();
                            Player fromPlayer = (Player) inviteContent[0];
                            String category = (String) inviteContent[1];
                            String difficulty = (String) inviteContent[2];
                            // Hiện lời mời ở BẤT KỲ màn nào (Menu, chọn chủ đề, sảnh, xếp hạng...), trừ khi đang trong trận
                            Platform.runLater(() -> showAcceptDialog(fromPlayer, category, difficulty));
                            break;

                        case INVITE_RESULT:
                            handleInviteResult(msg);
                            break;

                        case INVITE_ERROR:
                            String inviteErr = (String) msg.getContent();
                            Platform.runLater(() -> showNotice("Thông báo mời đấu", inviteErr));
                            break;

                        case LOGOUT_SUCCESS:
                            Platform.runLater(() -> {
                                AlertController.showAlert("Đăng xuất", "Bạn đã đăng xuất.");
                                showLoginUI();
                                // dừng nhạc nhưng giữ lựa chọn Bật/Tắt cho lần đăng nhập sau
                                if (mediaPlayer != null) mediaPlayer.stop();
                            });
                            break;

                        case SIGN_UP_SUCCESS:
                            String successMsg = (String) msg.getContent();
                            Platform.runLater(() -> {
                                AlertController.showAlert("Đăng ký thành công", "Tạo tài khoản thành công, mời bạn đăng nhập.");
                                showLoginUI();
                            });
                            break;

                        case SIGN_UP_FAILURE:
                            String signUpErr = (String) msg.getContent();
                            Platform.runLater(() -> {
                                if (signUpController != null) {
                                    signUpController.showError(signUpErr);
                                }
                            });
                            break;

                        case GET_RANKING_SUCCESS:
                            List<Player> playersRanking = (List<Player>) msg.getContent();
                            Platform.runLater(() -> showRankingUI(new ArrayList<>(playersRanking)));
                            break;

                        case GET_HISTORY_SUCCESS:
                            List<Player> playersHistory = (List<Player>) msg.getContent();
                            Platform.runLater(() -> showHistoryUI(new ArrayList<>(playersHistory)));
                            break;

                        case GET_HISTORY_DETAIL_SUCCESS:
                            Object[] detailContent = (Object[]) msg.getContent();
                            List<DetailMatch> details = (List<DetailMatch>) detailContent[0];
                            Platform.runLater(() -> showHistoryDetailUI(details));
                            break;

                        case ONLINE_LIST_UPDATE:
                            handleOnlineListUpdate(msg);
                            break;

                        case GAME_COUNTDOWN:
                            handleGameCountdown(msg);
                            break;

                        case START_GAME:
                            handleStartGame(msg);
                            break;

                        case FLIP_CARD_RESULT:
                            handleShowCardFlip(msg);
                            break;

                        case FLIP_ONE_CARD:
                            handleOpponentFlipOne(msg);
                            break;

                        case DRAW_OFFER:
                            Platform.runLater(() -> {
                                if (gameRoomController != null) gameRoomController.handleDrawOffer(String.valueOf(msg.getContent()));
                            });
                            break;

                        case DRAW_REJECTED:
                            Platform.runLater(() -> {
                                if (gameRoomController != null) gameRoomController.handleDrawRejected(String.valueOf(msg.getContent()));
                            });
                            break;

                        case CARD_MATCHED_RESULT:
                            handleCardsMatched(msg);
                            break;

                        case CARD_NOT_MATCHED_RESULT:
                            handleCardsNotMatched(msg);
                            break;

                        case TURN_CHANGED:
                            handleTurnChanged(msg);
                            break;

                        case TURN_TIMEOUT:
                            handleTurnTimeout(msg);
                            break;

                        case KICK_DUE_TO_TIMEOUT:
                            handleKickDueToTimeout(msg);
                            break;

                        case GAME_END:
                            handleGameEnd(msg);
                            break;

                        case PLAY_AGAIN_REQUEST:
                            handlePlayAgainRequest(msg);
                            break;

                        case REJECT_PLAY_AGAIN:
                        case RETURN_TO_LOBBY:
                            handleReturnToLobby(msg);
                            break;

                        case QUIT_GAME_SUCCESS:
                            handleQuitGame(msg);
                            break;

                        case ERROR:
                            handleServerError(msg);
                            break;

                        default:
                            System.out.println("Unknown message type: " + msg.getType());
                    }
                }
            } catch (Exception e) {
                boolean unexpected = isConnected;   // close() chủ động sẽ đặt isConnected = false trước
                System.err.println("Mất kết nối tới Server: " + e.getMessage());
                close();
                if (unexpected) {
                    handleDisconnection();
                }
            }
        }, "server-listener");
        listener.setDaemon(true);
        listener.start();
    }

    public void close() {
        isConnected = false;   // đặt trước để luồng lắng nghe biết đây là đóng chủ động
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- CÁC HÀM XỬ LÝ SỰ KIỆN TỪ SERVER ---

    private void handleOnlineListUpdate(Message message) {
        List<Player> onlinePlayers = (List<Player>) message.getContent();
        Platform.runLater(() -> {
            if (mainController != null) {
                mainController.updatePlayersList(onlinePlayers);
            }
        });
    }

    private void handleGameCountdown(Message msg) {
        Object[] countdownData = (Object[]) msg.getContent();
        int count = (int) countdownData[0];
        Platform.runLater(() -> {
            if (gameRoomController != null) {
                gameRoomController.handleCountdown(count);
            }
        });
    }

    private void handleStartGame(Message msg) {
        Map<String, Object> gameData = (Map<String, Object>) msg.getContent();
        Platform.runLater(() -> {
            if (gameRoomController != null) {
                gameRoomController.handleGameStart(gameData);
            }
        });
    }

    private void handleCardsMatched(Message msg) {
        Object[] matchData = (Object[]) msg.getContent();
        Platform.runLater(() -> {
            if (gameRoomController != null) {
                gameRoomController.handleCardsMatched(matchData);
            }
        });
    }

    private void handleCardsNotMatched(Message msg) {
        Object[] unmatchData = (Object[]) msg.getContent();
        Platform.runLater(() -> {
            if (gameRoomController != null) {
                gameRoomController.handleCardsNotMatched(unmatchData);
            }
        });
    }

    private void handleOpponentFlipOne(Message msg) {
        Platform.runLater(() -> {
            if (gameRoomController != null && msg.getContent() instanceof Integer) {
                gameRoomController.handleOpponentFlipOne((Integer) msg.getContent());
            }
        });
    }

    private void handleShowCardFlip(Message msg) {
        Platform.runLater(() -> {
            if (gameRoomController != null) {
                gameRoomController.handleShowFlip(msg);
            }
        });
    }

    private void handleTurnChanged(Message msg) {
        Map<String, Object> turnData = (Map<String, Object>) msg.getContent();
        Platform.runLater(() -> {
            if (gameRoomController != null) {
                gameRoomController.handleTurnChanged(turnData);
            }
        });
    }

    private void handleTurnTimeout(Message msg) {
        Map<String, Object> timeoutData = (Map<String, Object>) msg.getContent();
        Platform.runLater(() -> {
            if (gameRoomController != null) {
                gameRoomController.handleTurnTimeout(timeoutData);
            }
        });
    }

    private void handleKickDueToTimeout(Message msg) {
        String kickedPlayer = (String) msg.getContent();
        Platform.runLater(() -> {
            AlertController.showAlert("Xử thua", "Người chơi " + kickedPlayer + " đã bị xử thua do quá giờ 3 lần liên tiếp!");
        });
    }

    private void handleGameEnd(Message msg) {
        Map<String, Object> resultData = (Map<String, Object>) msg.getContent();
        Platform.runLater(() -> {
            if (gameRoomController != null) {
                gameRoomController.handleGameEnd(resultData);
            }
        });
    }

    private void handlePlayAgainRequest(Message msg) {
        Platform.runLater(() -> {
            if (gameRoomController != null) {
                // Hiển thị thông báo hoặc cập nhật UI trạng thái đối thủ muốn chơi lại
                System.out.println("Đối thủ yêu cầu tái đấu.");
            }
        });
    }

    private void handleReturnToLobby(Message msg) {
        String notice = msg.getContent() != null ? msg.getContent().toString() : "Trận đấu kết thúc, quay về sảnh chính.";
        Platform.runLater(() -> {
            leaveGameRoom();
            showMainUI();
            if (mainController != null) {
                mainController.showAlert("Thông báo", notice);
            }
        });
    }

    private void handleQuitGame(Message msg) {
        String content = (String) msg.getContent();
        Platform.runLater(() -> {
            leaveGameRoom();
            showMainUI();
            if (mainController != null) {
                mainController.showAlert("Thông báo", content);
            }
        });
    }

    // ===== Chuyển màn có hiệu ứng mờ dần + tiếng bấm nút =====
    private static final javafx.scene.media.AudioClip SOUND_CLICK = loadClip("/assets/sound/click.wav");

    private static javafx.scene.media.AudioClip loadClip(String path) {
        try {
            URL url = ClientService.class.getResource(path);
            return url == null ? null : new javafx.scene.media.AudioClip(url.toExternalForm());
        } catch (Exception e) {
            return null;
        }
    }

    /** Tiếng "bấm" khi nhấn nút (tắt theo nút Âm thanh). */
    public void playClick() {
        if (SOUND_CLICK != null && musicOn) SOUND_CLICK.play(0.6);
    }

    private void switchScene(Scene scene) {
        // mọi nút trong màn (trừ lá bài - đã có tiếng lật riêng) phát tiếng bấm
        scene.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            if (e.getTarget() instanceof javafx.scene.control.ButtonBase
                    && !((javafx.scene.Node) e.getTarget()).getStyleClass().contains("memory-card")) {
                playClick();
            }
        });
        Parent root = scene.getRoot();
        root.setOpacity(0);
        primaryStage.setScene(scene);
        javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(javafx.util.Duration.millis(220), root);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    /** Hộp thoại nhận lời mời, dùng chung cho mọi màn hình. */
    public void showAcceptDialog(Player fromPlayer, String category, String difficulty) {
        if (gameRoomController != null || currentPlayer == null) {
            // đang trong trận (hoặc chưa đăng nhập) -> tự từ chối để người mời không phải chờ
            sendMessage(new Message(MessageType.INVITE_RESPONSE, new Object[]{currentPlayer, fromPlayer, false, category, difficulty}));
            return;
        }
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
        alert.setTitle("Lời mời thách đấu");
        alert.setHeaderText(fromPlayer.getUsername() + " mời bạn thi đấu!");
        alert.setContentText("Chủ đề: " + themeName(category) + "\nĐộ khó: " + difficultyLabel(difficulty));
        styleDialog(alert);
        javafx.scene.control.ButtonType ok = new javafx.scene.control.ButtonType("Chấp nhận", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        javafx.scene.control.ButtonType no = new javafx.scene.control.ButtonType("Từ chối", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(ok, no);
        alert.showAndWait().ifPresent(r -> {
            boolean accepted = (r == ok);
            sendMessage(new Message(MessageType.INVITE_RESPONSE, new Object[]{currentPlayer, fromPlayer, accepted, category, difficulty}));
        });
    }

    /** Thông báo ngắn kiểu game, dùng được ở mọi màn hình. */
    public void showNotice(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        styleDialog(alert);
        alert.getButtonTypes().setAll(new javafx.scene.control.ButtonType("Ok", javafx.scene.control.ButtonBar.ButtonData.OK_DONE));
        alert.showAndWait();
    }

    public static void styleDialog(javafx.scene.control.Dialog<?> dialog) {
        javafx.scene.control.DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("my-alert");
        URL css = ClientService.class.getResource("/client/view/main.css");
        if (css != null) pane.getStylesheets().add(css.toExternalForm());
    }

    private void handleInviteResult(Message msg) {
        Object[] msgContent = (Object[]) msg.getContent();
        Player playerSendRequest = (Player) msgContent[0];
        Player playerReceiveRequest = (Player) msgContent[1];
        boolean result = (boolean) msgContent[2];

        if (result) {
            Platform.runLater(() -> {
                showGameRoomUI();
                if (gameRoomController != null) {
                    gameRoomController.setPlayers(playerSendRequest, playerReceiveRequest);
                }
            });
        } else {
            // msgContent[0] là người được mời (người vừa từ chối), msgContent[1] là chính mình (người mời)
            Platform.runLater(() -> showNotice("Lời mời bị từ chối", playerSendRequest.getUsername() + " đã từ chối lời mời!"));
        }
    }

    private void handleDisconnection() {
        isConnected = false;
        Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Lỗi kết nối");
            alert.setHeaderText("Mất kết nối với máy chủ");
            alert.setContentText("Kết nối tới Server đã bị đóng. Hãy bật lại Server rồi đăng nhập lại.");
            alert.showAndWait();

            leaveGameRoom();
            showLoginUI();
        });
    }

    private void handleServerError(Message msg) {
        String error = msg.getContent() != null ? msg.getContent().toString() : "Lỗi không xác định.";
        System.err.println("Server Error: " + error);
        Platform.runLater(() -> {
            // Đang ở bàn chơi đã kết thúc mà phòng phía server đã đóng (vd: bấm "Chơi tiếp" quá muộn) -> về sảnh.
            if (gameRoomController != null && gameRoomController.isGameOver()) {
                leaveGameRoom();
                showMainUI();
                if (mainController != null) {
                    mainController.showAlert("Thông báo", "Phòng chơi đã đóng, bạn được đưa về sảnh chính.");
                }
            } else {
                AlertController.showAlert("Lỗi", error);
            }
        });
    }

    /** Đóng các hộp thoại còn mở ở bàn chơi trước khi chuyển màn, tránh gửi thêm lệnh thừa lên server. */
    private void leaveGameRoom() {
        if (gameRoomController != null) {
            gameRoomController.closeOpenDialogs();
            gameRoomController = null;
        }
    }

    // --- CÁC HÀM ĐIỀU HƯỚNG GIAO DIỆN (UI ROUTING) ---

    public void showSignUpUI() {
        try {
            URL fxml = getClass().getResource("/client/view/SignUpUI.fxml");
            FXMLLoader loader = new FXMLLoader(fxml);
            Scene scene = new Scene(loader.load());

            signUpController = loader.getController();
            signUpController.setClient(this);

            URL cssLocation = getClass().getResource("/client/view/style.css");
            if (cssLocation != null) {
                scene.getStylesheets().add(cssLocation.toExternalForm());
            }

            primaryStage.setTitle("Lật Bài Online - Đăng ký");
            switchScene(scene);
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showLoginUI() {
        try {
            URL fxml = getClass().getResource("/client/view/LoginUI.fxml");
            FXMLLoader loader = new FXMLLoader(fxml);
            Scene scene = new Scene(loader.load());

            loginController = loader.getController();
            loginController.setClient(this);

            switchScene(scene);
            primaryStage.setTitle("Lật Bài Online - Đăng nhập");
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Menu chính sau khi đăng nhập: Play, Độ khó, Âm thanh, Bảng xếp hạng, Lịch sử đấu, Hướng dẫn, Đăng xuất. */
    public void showMenuUI() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/view/MenuUI.fxml"));
            Scene scene = new Scene(loader.load());
            MenuController controller = loader.getController();
            controller.setClient(this);
            primaryStage.setTitle("Lật Bài Online - Menu");
            switchScene(scene);
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Màn chọn chủ đề (bấm PLAY ở Menu). */
    public void showThemeSelectUI() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/view/ThemeSelectUI.fxml"));
            Scene scene = new Scene(loader.load());
            ThemeSelectController controller = loader.getController();
            controller.setClient(this, scene);
            primaryStage.setTitle("Lật Bài Online - Chọn chủ đề");
            switchScene(scene);
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showMainUI() {
        try {
            URL fxml = getClass().getResource("/client/view/MainUI.fxml");
            FXMLLoader loader = new FXMLLoader(fxml);
            Scene scene = new Scene(loader.load());

            mainController = loader.getController();
            mainController.setClient(this);
            mainController.setUserLogin(currentPlayer);

            primaryStage.setTitle("Lật Bài Online - Sảnh chờ");
            switchScene(scene);
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showRankingUI(List<Player> rankingList) {
        try {
            URL fxml = getClass().getResource("/client/view/RankingUI.fxml");
            FXMLLoader loader = new FXMLLoader(fxml);
            Scene scene = new Scene(loader.load());

            rankingController = loader.getController();
            rankingController.setClient(this);
            rankingController.setRankingList(rankingList);

            URL cssLocation = getClass().getResource("/client/view/ranking.css");
            if (cssLocation != null) {
                scene.getStylesheets().add(cssLocation.toExternalForm());
            }

            primaryStage.setTitle("Lật Bài Online - Bảng xếp hạng");
            switchScene(scene);
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showHistoryUI(List<Player> historyList) {
        try {
            this.cachedHistoryList = historyList;
            URL fxml = getClass().getResource("/client/view/HistoryUI.fxml");
            FXMLLoader loader = new FXMLLoader(fxml);
            Scene scene = new Scene(loader.load());

            historyController = loader.getController();
            historyController.setClient(this);
            if (currentPlayer != null) {
                historyController.setWelcomeText("Lịch sử đấu");
            }
            historyController.setHistoryList(historyList);

            switchScene(scene);
            primaryStage.setTitle("Lật Bài Online - Lịch sử đấu");
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showHistoryDetailUI(List<DetailMatch> details) {
        try {
            URL fxml = getClass().getResource("/client/view/DetailHistoryUI.fxml");
            FXMLLoader loader = new FXMLLoader(fxml);
            Scene scene = new Scene(loader.load());

            HistoryDetailController controller = loader.getController();
            controller.setClient(this);

            String playerName = getSelectedPlayerName() != null ? getSelectedPlayerName() : "Unknown";
            controller.setHistoryDetail(details, playerName);

            URL cssLocation = getClass().getResource("/client/view/style.css");
            if (cssLocation != null) {
                scene.getStylesheets().add(cssLocation.toExternalForm());
            }

            switchScene(scene);
            primaryStage.setTitle("Lật Bài Online - Lịch sử: " + playerName);
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showGameRoomUI() {
        try {
            URL fxml = getClass().getResource("/client/view/GameRoomUI.fxml");
            FXMLLoader loader = new FXMLLoader(fxml);
            Scene scene = new Scene(loader.load());

            gameRoomController = loader.getController();
            gameRoomController.setClient(this);

            URL cssLocation = getClass().getResource("/client/view/style.css");
            if (cssLocation != null) {
                scene.getStylesheets().add(cssLocation.toExternalForm());
            }

            primaryStage.setTitle("Lật Bài Online - Trận đấu");
            switchScene(scene);
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- GETTERS & SETTERS / AUDIO ---

    public void setGameRoomController(GameRoomController gameRoomController) {
        this.gameRoomController = gameRoomController;
    }

    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public boolean isMusicOn() {
        return musicOn;
    }

    public void setMusicOn(boolean musicOn) {
        this.musicOn = musicOn;
        if (mediaPlayer != null) {
            if (musicOn) {
                mediaPlayer.play();
            } else {
                mediaPlayer.pause();
            }
        }
    }

    private void setupMusic() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.dispose();
                mediaPlayer = null;
            }
            URL musicUrl = getClass().getResource(MUSIC_PATH);
            if (musicUrl == null) {
                System.err.println("Chưa có file nhạc " + MUSIC_PATH + " (xem src/assets/README.md)");
                return;
            }
            String musicPath = musicUrl.toExternalForm();
            Media media = new Media(musicPath);
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setVolume(0.3);
            mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
            if (musicOn) {
                mediaPlayer.play();
            }
        } catch (Exception e) {
            System.err.println("Không thể tải file nhạc: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        javafx.application.Application.launch(ClientRunApp.class, args);
    }
}