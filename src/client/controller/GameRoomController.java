package client.controller;

import client.service.ClientService;
import constants.MessageType;
import entity.Message;
import entity.Player;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.*;

public class GameRoomController implements Initializable {

    // Khop voi GameRoom: kich thuoc luoi duoc server gui qua START_GAME
    private int gridRows = 4;  // mac dinh hard
    private int gridCols = 6;
    private static final int CARD_SIZE = 90;
    private static final int TURN_TIME_SECONDS = 15;
    private static final int DEFAULT_MATCH_SECONDS = 15 * 60;   // trùng GameRoom.MATCH_TIMEOUT_MINUTES
    // Ảnh nền mặc định — dùng chung cho tất cả chủ đề: messi, giang_ho_mang, truong_con_nam_dinh
    private static final String DEFAULT_BG        = "/assets/images/background/background_default.jpg";
    private static final String DEFAULT_BACK_CARD = "/assets/images/background/card_back.jpg"; // mặt sau lá bài
    private String currentBackCardImagePath = null;

    // Map chủ đề để trống → tất cả fallback về DEFAULT_BG / DEFAULT_BACK_CARD
    private static final Map<String, String> THEME_BACK_CARDS  = new HashMap<>();
    private static final Map<String, String> THEME_BACKGROUNDS = new HashMap<>();

    private ClientService app;
    private Player userPlayer;
    private Player opponentPlayer;

    private int serverPlayer1Id;
    private int serverPlayer2Id;

    private boolean isMyTurn = false;
    private boolean isProcessingMove = false;
    private boolean isGameOver = false;

    private Map<Integer, String> imageCache;
    private int[] matrixConfig;
    private final Set<Integer> matchedCardIndices = new HashSet<>();

    private Integer firstCardIndex = null;
    private Button firstCardButton = null;
    private Integer opponentRevealedIndex = null;     // lá đầu tiên đối thủ vừa lật (đang ngửa trên máy mình)
    private PauseTransition pendingFlipBack = null;   // đang chờ úp 2 lá lật sai
    private int[] pendingFlipBackCards = null;

    @FXML private VBox rootPane;
    @FXML private GridPane tblCard;
    @FXML private Button btnHome;
    @FXML private Button btnMusic;
    @FXML private ProgressBar timeBar;
    @FXML private Label timeLabel;
    @FXML private Label txtPlayer1;
    @FXML private Label txtPlayer2;
    @FXML private Label txtScore1;
    @FXML private Label txtScore2;
    @FXML private Label txtScore1Value;
    @FXML private Label txtScore2Value;
    @FXML private Label lblTurnIndicator;
    @FXML private AnchorPane background;
    @FXML private Label lblMatchTime;
    @FXML private Button btnDraw;
    @FXML private Button btnSurrender;
    @FXML private HBox drawBanner;
    @FXML private Label lblDrawBanner;
    @FXML private Button btnDrawAccept;
    @FXML private Button btnDrawDecline;

    // Cầu hòa: mỗi người tối đa 3 lần / trận (server cũng kiểm tra)
    private static final int MAX_DRAW_OFFERS = 3;
    private int drawOffersLeft = MAX_DRAW_OFFERS;
    private boolean myDrawPending = false;          // mình đã xin hòa, đang chờ đối thủ
    private boolean incomingDrawOffer = false;      // đối thủ đang xin hòa mình
    private PauseTransition bannerHideTimer = null;

    // Hiệu ứng âm thanh (file trong src/assets/sound/). Thiếu file thì bỏ qua, game vẫn chạy.
    private static final javafx.scene.media.AudioClip SOUND_FLIP = loadSound("/assets/sound/lat_the.mp3");
    private static final javafx.scene.media.AudioClip SOUND_MATCH = loadSound("/assets/sound/lat_thang.mp3");
    private static final javafx.scene.media.AudioClip SOUND_WRONG = loadSound("/assets/sound/lat_khong_dung.mp3");

    private static javafx.scene.media.AudioClip loadSound(String path) {
        try {
            URL url = GameRoomController.class.getResource(path);
            return url == null ? null : new javafx.scene.media.AudioClip(url.toExternalForm());
        } catch (Exception e) {
            System.err.println("Không tải được âm thanh " + path + ": " + e.getMessage());
            return null;
        }
    }

    /** Phát hiệu ứng nếu đang bật âm thanh (nút Âm thanh ở Menu / nút Nhạc trong trận). */
    private void playSound(javafx.scene.media.AudioClip clip) {
        if (clip != null && app != null && app.isMusicOn()) {
            clip.play();
        }
    }

    private boolean isMusicOn;
    private Timeline turnTimeline;
    private Timeline matchTimeline;
    private Alert resultAlert;
    private boolean leavingRoom = false;

    @FXML private StackPane countdownOverlay;
    @FXML private Label countdownLabel;
    @FXML private Pane overlayBackground;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        tblCard.setDisable(true);
        if (countdownOverlay != null) countdownOverlay.setVisible(false);
        if (overlayBackground != null) overlayBackground.setVisible(false);
        if (countdownLabel != null) {
            countdownLabel.setFont(Font.font("Impact", FontWeight.BOLD, 150));
        }
    }

    public void setClient(ClientService app) {
        this.app = app;
        this.isMusicOn = app.isMusicOn();
        setupMusic();
        app.setGameRoomController(this);
    }

    public void setPlayers(Player player1, Player player2) {
        Player currentPlayer = app.getCurrentPlayer();
        if (currentPlayer != null && player1.getId() == currentPlayer.getId()) {
            this.userPlayer = player1;
            this.opponentPlayer = player2;
        } else {
            this.userPlayer = player2;
            this.opponentPlayer = player1;
        }

        Platform.runLater(() -> {
            txtPlayer1.setText(this.userPlayer.getUsername());
            txtPlayer2.setText(this.opponentPlayer.getUsername());
            txtScore1Value.setText("0");
            txtScore2Value.setText("0");
            applyPlayerColors();
        });
    }

    private void setupMusic() {
        if (btnMusic != null) {
            btnMusic.setText(isMusicOn ? "Nhạc: Bật" : "Nhạc: Tắt");
        }
    }

    /** Server gán người mời (player1) màu ĐỎ, người được mời (player2) màu XANH. */
    private void applyPlayerColors() {
        if (userPlayer == null || serverPlayer1Id == 0) return;
        boolean iAmPlayer1 = userPlayer.getId() == serverPlayer1Id;
        String red = "-fx-text-fill: #e84b3b;";
        String blue = "-fx-text-fill: #3b8fe8;";
        txtPlayer1.setStyle(iAmPlayer1 ? red : blue);
        txtPlayer2.setStyle(iAmPlayer1 ? blue : red);
    }

    private void changeBackgroundByTheme(String themeName) {
        if (background == null) return;
        // Tất cả chủ đề dùng chung background_default.jpg
        String imagePath = THEME_BACKGROUNDS.getOrDefault(themeName,
                           THEME_BACKGROUNDS.getOrDefault("Default", DEFAULT_BG));
        URL resourceUrl = getClass().getResource(imagePath);
        if (resourceUrl == null) return;

        try {
            Image image = new Image(resourceUrl.toExternalForm(), true);
            BackgroundImage bgImage = new BackgroundImage(
                    image, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.CENTER, new BackgroundSize(1.0, 1.0, true, true, false, true)
            );
            background.setBackground(new Background(bgImage));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void changeBackCardByTheme(String themeName) {
        // Tất cả chủ đề dùng chung background_default.jpg làm mặt sau
        this.currentBackCardImagePath = THEME_BACK_CARDS.getOrDefault(themeName,
                THEME_BACK_CARDS.getOrDefault("Default", DEFAULT_BACK_CARD));
    }

    public void handleCountdown(int count) {
        Platform.runLater(() -> showCountdownNumber(String.valueOf(count), getColorForCount(count)));
    }

    public void handleGameStart(Map<String, Object> gameData) {
        Platform.runLater(() -> {
            try {
                resetGameState();
                Object[] matrixConfigData = (Object[]) gameData.get("matrixConfig");
                this.matrixConfig = (int[]) matrixConfigData[0];
                this.imageCache = (Map<Integer, String>) matrixConfigData[2];

                Player serverP1 = (Player) gameData.get("player1");
                Player serverP2 = (Player) gameData.get("player2");

                this.serverPlayer1Id = serverP1.getId();
                this.serverPlayer2Id = serverP2.getId();

                setPlayers(serverP1, serverP2);

                int firstTurnPlayerId = (int) gameData.get("firstTurnPlayerId");
                String theme = (String) gameData.get("theme");

                // Nhan kich thuoc luoi tu server
                Object grRows = gameData.get("gridRows");
                Object grCols = gameData.get("gridCols");
                if (grRows instanceof Integer) this.gridRows = (Integer) grRows;
                if (grCols instanceof Integer) this.gridCols = (Integer) grCols;

                if (theme != null) {
                    changeBackgroundByTheme(theme);
                    changeBackCardByTheme(theme);
                }
                updateTurnState(firstTurnPlayerId);
                Object limit = gameData.get("matchTimeLimit");
                startMatchCountdown(limit instanceof Integer ? (Integer) limit : DEFAULT_MATCH_SECONDS);
                showCountdownStart();
            } catch (Exception e) {
                e.printStackTrace();
                showErrorDialog("Lỗi bắt đầu trận đấu: " + e.getMessage());
            }
        });
    }

    public void handleTurnChanged(Map<String, Object> turnData) {
        Platform.runLater(() -> {
            int currentPlayerId = (Integer) turnData.get("nextPlayerId");
            finishPendingFlipBack();          // bài sai chưa kịp úp thì úp ngay trước khi sang lượt mới
            if (incomingDrawOffer && currentPlayerId != (isMyTurn ? userPlayer.getId() : opponentPlayer.getId())) {
                incomingDrawOffer = false;    // sang lượt mới -> lời cầu hòa của đối thủ hết hạn
                hideDrawBanner();
            }
            opponentRevealedIndex = null;
            updateTurnState(currentPlayerId);
            resetCardSelection();
        });
    }

    public void handleCardsMatched(Object[] matchData) {
        Platform.runLater(() -> {
            playSound(SOUND_MATCH);
            int card1Index = (int) matchData[0];
            int card2Index = (int) matchData[1];
            Player matchedPlayer = (Player) matchData[2];
            int p1Score = (int) matchData[3];
            int p2Score = (int) matchData[4];
            String scoredColor = (String) matchData[5]; // Viền Đỏ (RED) hoặc Xanh (BLUE) từ Server

            processMatchAnimation(card1Index, card2Index, scoredColor);
            opponentRevealedIndex = null;
            matchedCardIndices.add(card1Index);
            matchedCardIndices.add(card2Index);

            int myFinalScore = (userPlayer.getId() == this.serverPlayer1Id) ? p1Score : p2Score;
            int opponentFinalScore = (userPlayer.getId() == this.serverPlayer1Id) ? p2Score : p1Score;

            txtScore1Value.setText(String.valueOf(myFinalScore));
            txtScore2Value.setText(String.valueOf(opponentFinalScore));

            if (matchedPlayer.getId() == userPlayer.getId()) {
                showScoreIncreaseAnimation(txtScore1Value, 1);
            } else {
                showScoreIncreaseAnimation(txtScore2Value, 1);
            }

            resetCardSelection();
        });
    }

    private void showScoreIncreaseAnimation(Label scoreLabel, int increment) {
        Label plusLabel = new Label("+" + increment);
        plusLabel.setStyle("-fx-text-fill: #2c8a1f; -fx-font-size: 28px; -fx-font-weight: bold;");

        StackPane parent = (StackPane) scoreLabel.getParent();
        parent.getChildren().add(plusLabel);
        StackPane.setAlignment(plusLabel, Pos.TOP_CENTER);

        TranslateTransition moveUp = new TranslateTransition(Duration.millis(600), plusLabel);
        moveUp.setFromY(10);
        moveUp.setToY(-20);

        FadeTransition fade = new FadeTransition(Duration.millis(600), plusLabel);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);

        ParallelTransition animation = new ParallelTransition(moveUp, fade);
        animation.setOnFinished(e -> parent.getChildren().remove(plusLabel));
        animation.play();
    }

    public void handleCardsNotMatched(Object[] unmatchData) {
        Platform.runLater(() -> {
            playSound(SOUND_WRONG);
            int card1Index = (int) unmatchData[0];
            int card2Index = (int) unmatchData[1];

            opponentRevealedIndex = null;
            finishPendingFlipBack();
            // Không gọi resetCardSelection() ở đây: việc chọn bài do TURN_CHANGED quản lý.
            // Trước đây reset ở đây làm mất lá người chơi kế tiếp vừa chọn -> lá đó kẹt ngửa.
            pendingFlipBackCards = new int[]{card1Index, card2Index};
            pendingFlipBack = new PauseTransition(Duration.millis(800));
            pendingFlipBack.setOnFinished(e -> finishPendingFlipBack());
            pendingFlipBack.play();
        });
    }

    public void handleGameEnd(Map<String, Object> resultData) {
        Platform.runLater(() -> {
            isGameOver = true;
            if (turnTimeline != null) turnTimeline.stop();
            if (matchTimeline != null) matchTimeline.stop();
            tblCard.setDisable(true);
            myDrawPending = false;
            incomingDrawOffer = false;
            hideDrawBanner();
            updateDrawButtons();
            if (leavingRoom) return;

            int winnerId = (int) resultData.get("winnerId");
            Object left = resultData.get("leftPlayerId");
            int leftPlayerId = (left instanceof Integer) ? (Integer) left : -1;
            if (leftPlayerId != -1) {
                if (leftPlayerId == userPlayer.getId()) return;   // chính mình bấm rời trận -> sắp về sảnh, không hiện kết quả
                showOpponentLeftDialog();
                return;
            }
            Object sur = resultData.get("surrenderedPlayerId");
            int surrenderedId = (sur instanceof Integer) ? (Integer) sur : -1;
            String text;
            if (surrenderedId == userPlayer.getId()) {
                text = "BẠN ĐÃ XIN THUA.";
            } else if (surrenderedId != -1) {
                text = "ĐỐI THỦ XIN THUA — BẠN THẮNG! 🎉";
            } else if (winnerId == -1) {
                text = "HÒA TRẬN!";
            } else if (winnerId == userPlayer.getId()) {
                text = "BẠN ĐÃ THẮNG CUỘC! 🎉";
            } else {
                text = "BẠN ĐÃ THUA CUỘC!";
            }
            showResultDialog("KẾT THÚC TRẬN ĐẤU", text);
        });
    }

    public void handleTurnTimeout(Map<String, Object> timeoutData) {
        Platform.runLater(() -> {
            // Hết giờ khi mới lật 1 lá -> úp lá đó lại (cả lá của mình lẫn lá đối thủ đang ngửa)
            if (firstCardIndex != null && !matchedCardIndices.contains(firstCardIndex)) {
                flipCardBackAnimation(firstCardIndex);
            }
            if (opponentRevealedIndex != null && !matchedCardIndices.contains(opponentRevealedIndex)) {
                flipCardBackAnimation(opponentRevealedIndex);
            }
            opponentRevealedIndex = null;
            resetCardSelection();
        });
    }

    /** Đối thủ vừa lật lá đầu tiên -> lật lá đó trên máy mình ngay. */
    public void handleOpponentFlipOne(int index) {
        if (isMyTurn || isGameOver || matchedCardIndices.contains(index)) return;
        findCardButton(index).ifPresent(btn -> flipCardToShow(btn, index, null));
        opponentRevealedIndex = index;
    }

    /** Úp ngay 2 lá lật sai đang chờ (nếu có). */
    private void finishPendingFlipBack() {
        if (pendingFlipBack == null) return;
        pendingFlipBack.setOnFinished(null);
        pendingFlipBack.stop();
        pendingFlipBack = null;
        int[] cards = pendingFlipBackCards;
        pendingFlipBackCards = null;
        if (cards != null) {
            for (int idx : cards) {
                if (!matchedCardIndices.contains(idx)) {
                    flipCardBackAnimation(idx);
                }
            }
        }
    }

    private void handleCardClick(int index, Button cardButton) {
        if (!isMyTurn || isProcessingMove || isGameOver || pendingFlipBack != null
                || matchedCardIndices.contains(index) || (firstCardIndex != null && firstCardIndex == index)) {
            return;
        }
        if (incomingDrawOffer) {   // lật bài tiếp = từ chối cầu hòa (server tự xử lý)
            incomingDrawOffer = false;
            hideDrawBanner();
        }

        isProcessingMove = true;

        if (firstCardIndex == null) {
            firstCardIndex = index;
            firstCardButton = cardButton;
            app.sendMessage(new Message(MessageType.FLIP_ONE_CARD, index));   // cho đối thủ thấy lá đầu ngay
            flipCardToShow(cardButton, index, () -> isProcessingMove = false);
        } else {
            flipCardToShow(cardButton, index, () -> {
                try {
                    Object[] moveData = {firstCardIndex, index, opponentPlayer};
                    app.sendMessage(new Message(MessageType.PLAYER_MOVE, moveData));
                } catch (Exception e) {
                    flipCardBackAnimation(firstCardIndex);
                    resetCardSelection();
                }
            });
        }
    }

    public void handleShowFlip(Message message) {
        Object[] content = (Object[]) message.getContent();
        Platform.runLater(() -> {
            int idx1 = (int) content[0];
            int idx2 = (int) content[1];
            Integer alreadyShown = opponentRevealedIndex;   // lá đầu đã ngửa nhờ FLIP_ONE_CARD -> không lật lại
            if (alreadyShown == null || alreadyShown != idx1) {
                findCardButton(idx1).ifPresent(btn -> flipCardToShow(btn, idx1, null));
            }
            if (alreadyShown == null || alreadyShown != idx2) {
                findCardButton(idx2).ifPresent(btn -> flipCardToShow(btn, idx2, null));
            }
            opponentRevealedIndex = null;
        });
    }

    private Optional<Button> findCardButton(int index) {
        return findNodeInGrid(tblCard, index).map(node -> (Button) node);
    }

    private void resetGameState() {
        if (matchTimeline != null) matchTimeline.stop();
        isMyTurn = false;
        isProcessingMove = false;
        isGameOver = false;
        matchedCardIndices.clear();
        if (pendingFlipBack != null) pendingFlipBack.stop();
        pendingFlipBack = null;
        pendingFlipBackCards = null;
        opponentRevealedIndex = null;
        resetCardSelection();
        drawOffersLeft = MAX_DRAW_OFFERS;
        myDrawPending = false;
        incomingDrawOffer = false;
        hideDrawBanner();
        updateDrawButtons();
        txtScore1Value.setText("0");
        txtScore2Value.setText("0");
        tblCard.getChildren().clear();
    }

    private void resetCardSelection() {
        firstCardIndex = null;
        firstCardButton = null;
        isProcessingMove = false;
    }

    private void updateTurnState(int currentPlayerId) {
        isMyTurn = (userPlayer != null && userPlayer.getId() == currentPlayerId);

        lblTurnIndicator.setText(isMyTurn ? "LƯỢT CỦA BẠN" : "LƯỢT ĐỐI THỦ");
        lblTurnIndicator.setTextFill(isMyTurn ? Color.web("#2c8a1f") : Color.web("#e07b00"));

        tblCard.setDisable(false);

        if (!isGameOver) {
            startTurnCountdown();
        }
    }

    private void startTurnCountdown() {
        if (turnTimeline != null) turnTimeline.stop();

        final int[] remainingTime = {TURN_TIME_SECONDS};
        timeLabel.setText(remainingTime[0] + "s");
        timeBar.setProgress(1.0);
        timeBar.setStyle("-fx-accent: #f5c623;");

        turnTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            remainingTime[0]--;
            timeLabel.setText(remainingTime[0] + "s");
            timeBar.setProgress((double) remainingTime[0] / TURN_TIME_SECONDS);

            if (remainingTime[0] <= 3) {
                timeBar.setStyle("-fx-accent: #e84b3b;");
            } else if (remainingTime[0] <= 6) {
                timeBar.setStyle("-fx-accent: #f5a623;");
            }

            if (remainingTime[0] <= 0) {
                turnTimeline.stop();
                timeLabel.setText("Hết giờ");
            }
        }));
        turnTimeline.setCycleCount(TURN_TIME_SECONDS);
        turnTimeline.play();
    }

    /** Đồng hồ tổng 15 phút của trận (chỉ để hiển thị; Server mới là bên quyết định hết giờ). */
    private void startMatchCountdown(int totalSeconds) {
        if (matchTimeline != null) matchTimeline.stop();
        final int[] remaining = {totalSeconds};
        updateMatchTimeLabel(remaining[0]);

        matchTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            remaining[0] = Math.max(0, remaining[0] - 1);
            updateMatchTimeLabel(remaining[0]);
            if (remaining[0] <= 0) {
                matchTimeline.stop();
            }
        }));
        matchTimeline.setCycleCount(totalSeconds);
        matchTimeline.play();
    }

    private void updateMatchTimeLabel(int seconds) {
        if (lblMatchTime == null) return;
        lblMatchTime.setText(String.format("%02d:%02d", seconds / 60, seconds % 60));
        lblMatchTime.getStyleClass().remove("time-warning");
        if (seconds <= 60) {
            lblMatchTime.getStyleClass().add("time-warning");
        }
    }

    public boolean isGameOver() {
        return isGameOver;
    }

    /** Gọi khi rời bàn chơi (về sảnh / mất kết nối): dừng đồng hồ và đóng hộp thoại kết quả nếu đang mở. */
    public void closeOpenDialogs() {
        leavingRoom = true;
        if (turnTimeline != null) turnTimeline.stop();
        if (matchTimeline != null) matchTimeline.stop();
        if (resultAlert != null && resultAlert.isShowing()) {
            resultAlert.close();
        }
    }

    private void createGameBoard() {
        tblCard.getChildren().clear();
        double delay = 0;
        for (int i = 0; i < gridRows * gridCols; i++) {
            int row = i / gridCols;
            int col = i % gridCols;
            Button cardButton = createCardButton(i);
            tblCard.add(cardButton, col, row);
            dealCardEffect(cardButton, delay);
            delay += 20;
        }
    }

    private Button createCardButton(int index) {
        Button card = new Button();
        card.setPrefSize(CARD_SIZE, CARD_SIZE);
        card.setMinSize(CARD_SIZE, CARD_SIZE);
        card.setMaxSize(CARD_SIZE, CARD_SIZE);
        card.setPadding(javafx.geometry.Insets.EMPTY);  // bỏ padding để ảnh fill sát viền
        card.getStyleClass().add("memory-card");
        card.setUserData(index);

        ImageView imageView = createImageViewFromPath(currentBackCardImagePath);
        card.setGraphic(imageView);
        if (imageView.getImage() == null) {
            card.setText("?");   // chưa có ảnh mặt sau -> hiện dấu ? thay cho ảnh
        }
        card.setOnAction(event -> handleCardClick(index, card));

        card.setOnMouseEntered(e -> {
            if (isMyTurn && !isProcessingMove && !isGameOver && !matchedCardIndices.contains(index)) {
                card.setScaleX(1.05);
                card.setScaleY(1.05);
            }
        });
        card.setOnMouseExited(e -> {
            card.setScaleX(1.0);
            card.setScaleY(1.0);
        });

        return card;
    }

    private void flipCardToShow(Button card, int index, Runnable onFinished) {
        if (card == null) return;
        String base64Image = imageCache.get(matrixConfig[index]);
        ImageView frontView = createImageViewFromBase64(base64Image);
        playSound(SOUND_FLIP);
        createFlipAnimation(card, frontView, onFinished).play();
        card.setText(null);
    }

    private void flipCardBackAnimation(int index) {
        findCardButton(index).ifPresent(card -> {
            ImageView backView = createImageViewFromPath(currentBackCardImagePath);
            createFlipAnimation(card, backView, null).play();
            card.setText(backView.getImage() == null ? "?" : null);
        });
    }

    private void processMatchAnimation(int index1, int index2, String scoredColor) {
        findCardButton(index1).ifPresent(card -> addMatchEffect(card, scoredColor));
        findCardButton(index2).ifPresent(card -> addMatchEffect(card, scoredColor));
    }

    // Hiệu ứng viền sáng DropShadow màu Đỏ/Xanh theo người ăn bài
    private void addMatchEffect(Button card, String scoredColor) {
        card.setDisable(true);
        Color glowColor = "RED".equalsIgnoreCase(scoredColor) ? Color.RED : Color.DODGERBLUE;
        DropShadow glow = new DropShadow(25, glowColor);
        glow.setSpread(0.6);
        card.setEffect(glow);

        ScaleTransition scale = new ScaleTransition(Duration.millis(200), card);
        scale.setToX(1.1);
        scale.setToY(1.1);
        scale.setAutoReverse(true);
        scale.setCycleCount(2);
        scale.play();
    }

    private SequentialTransition createFlipAnimation(Button card, ImageView newView, Runnable onFinished) {
        RotateTransition rotateOut = new RotateTransition(Duration.millis(150), card);
        rotateOut.setAxis(javafx.scene.transform.Rotate.Y_AXIS);
        rotateOut.setFromAngle(0);
        rotateOut.setToAngle(90);
        rotateOut.setInterpolator(Interpolator.EASE_IN);

        RotateTransition rotateIn = new RotateTransition(Duration.millis(150), card);
        rotateIn.setAxis(javafx.scene.transform.Rotate.Y_AXIS);
        rotateIn.setFromAngle(90);
        rotateIn.setToAngle(0);
        rotateIn.setInterpolator(Interpolator.EASE_OUT);

        rotateOut.setOnFinished(e -> card.setGraphic(newView));

        SequentialTransition sequence = new SequentialTransition(rotateOut, rotateIn);
        if (onFinished != null) {
            sequence.setOnFinished(e -> onFinished.run());
        }
        return sequence;
    }

    private void showCountdownNumber(String number, Color color) {
        overlayBackground.setVisible(true);
        countdownOverlay.setVisible(true);
        countdownLabel.setText(number);
        countdownLabel.setTextFill(color);

        DropShadow glow = new DropShadow(50, color);
        glow.setSpread(0.2);
        countdownLabel.setEffect(glow);

        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(300), countdownLabel);
        scaleIn.setFromX(0.2); scaleIn.setFromY(0.2);
        scaleIn.setToX(1.5); scaleIn.setToY(1.5);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), countdownLabel);
        fadeIn.setFromValue(0.0); fadeIn.setToValue(1.0);

        ParallelTransition entrance = new ParallelTransition(scaleIn, fadeIn);

        ScaleTransition scaleOut = new ScaleTransition(Duration.millis(300), countdownLabel);
        scaleOut.setToX(0.5); scaleOut.setToY(0.5);
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), countdownLabel);
        fadeOut.setToValue(0.0);

        ParallelTransition exit = new ParallelTransition(scaleOut, fadeOut);

        SequentialTransition sequence = new SequentialTransition(
                entrance, new PauseTransition(Duration.millis(200)), exit
        );
        sequence.play();
    }

    private void showCountdownStart() {
        countdownLabel.setText("START!");
        countdownLabel.setTextFill(Color.web("#ffd23f"));

        DropShadow glow = new DropShadow(70, Color.LIME);
        glow.setSpread(0.3);
        countdownLabel.setEffect(glow);

        ScaleTransition explode = new ScaleTransition(Duration.millis(250), countdownLabel);
        explode.setFromX(0.1); explode.setFromY(0.1);
        explode.setToX(2.0); explode.setToY(2.0);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(250), countdownLabel);
        fadeIn.setFromValue(0.0); fadeIn.setToValue(1.0);

        ParallelTransition entrance = new ParallelTransition(explode, fadeIn);

        FadeTransition fadeOutOverlay = new FadeTransition(Duration.millis(200), overlayBackground);
        fadeOutOverlay.setToValue(0);

        FadeTransition fadeOutLabel = new FadeTransition(Duration.millis(200), countdownLabel);
        fadeOutLabel.setToValue(0);

        ParallelTransition exitTransition = new ParallelTransition(fadeOutOverlay, fadeOutLabel);
        exitTransition.setOnFinished(e -> {
            createGameBoard();
            overlayBackground.setVisible(false);
            countdownOverlay.setVisible(false);
            overlayBackground.setOpacity(1.0);
        });

        SequentialTransition fullSequence = new SequentialTransition(
                entrance, new PauseTransition(Duration.millis(500)), exitTransition
        );
        fullSequence.play();
    }

    private Color getColorForCount(int count) {
        return switch (count) {
            case 3 -> Color.YELLOWGREEN;
            case 2 -> Color.ORANGE;
            case 1 -> Color.YELLOW;
            default -> Color.WHITE;
        };
    }

    private void dealCardEffect(Node card, double delay) {
        card.setScaleX(0.1);
        card.setScaleY(0.1);
        card.setOpacity(0.0);

        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(300), card);
        scaleIn.setToX(1.0); scaleIn.setToY(1.0);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), card);
        fadeIn.setToValue(1.0);

        ParallelTransition entrance = new ParallelTransition(scaleIn, fadeIn);
        entrance.setDelay(Duration.millis(delay));
        entrance.play();
    }

    @FXML
    private void onHomeButton(ActionEvent event) {
        if (isGameOver) {
            leaveAfterGame();
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Nếu thoát bây giờ, bạn sẽ bị xử thua. Bạn có chắc không?", ButtonType.YES, ButtonType.NO);
        alert.setTitle("Xác nhận thoát");
        alert.setHeaderText("Bạn muốn rời trận đấu?");
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStyleClass().add("my-alert");

        URL cssFile = AlertController.class.getResource("/client/view/main.css");
        if (cssFile != null) {
            dialogPane.getStylesheets().add(cssFile.toExternalForm());
        }

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                goHome();
            }
        });
    }

    /** Sau khi trận kết thúc: báo không chơi lại rồi rời phòng. */
    private void leaveAfterGame() {
        if (leavingRoom) return;
        app.sendMessage(new Message(MessageType.REJECT_PLAY_AGAIN, userPlayer));
        goHome();
    }

    private void goHome() {
        try {
            Object[] sendContent = {userPlayer, opponentPlayer};
            app.sendMessage(new Message(MessageType.QUIT_GAME, sendContent));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onMusicButton() {
        isMusicOn = !isMusicOn;
        app.setMusicOn(isMusicOn);
        setupMusic();
    }

    private ImageView createImageViewFromBase64(String base64) {
        try {
            String base64Data = base64.substring(base64.indexOf(",") + 1);
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            Image image = new Image(new ByteArrayInputStream(imageBytes));
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(CARD_SIZE);
            imageView.setFitHeight(CARD_SIZE);
            imageView.setPreserveRatio(false);  // stretch vừa khít khung
            imageView.setSmooth(true);
            return imageView;
        } catch (Exception e) {
            return createImageViewFromPath(null);
        }
    }

    private ImageView createImageViewFromPath(String path) {
        ImageView imageView = new ImageView();
        String realPath = path != null ? path : currentBackCardImagePath;
        if (realPath == null) return imageView;
        URL url = getClass().getResource(realPath);
        if (url == null) {
            return imageView;   // chưa có ảnh -> trả về ImageView rỗng, không làm crash
        }
        try {
            Image image = new Image(url.toExternalForm());
            imageView.setImage(image);
            imageView.setFitWidth(CARD_SIZE);
            imageView.setFitHeight(CARD_SIZE);
            imageView.setPreserveRatio(false);  // stretch vừa khít khung
            imageView.setSmooth(true);
        } catch (Exception e) {
            System.err.println("Lỗi tải ảnh: " + path);
        }
        return imageView;
    }

    private Optional<Node> findNodeInGrid(GridPane gridPane, int index) {
        int col = index % gridCols;
        int row = index / gridCols;
        for (Node node : gridPane.getChildren()) {
            Integer r = GridPane.getRowIndex(node);
            Integer c = GridPane.getColumnIndex(node);
            if (r != null && c != null && r == row && c == col) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    private void showErrorDialog(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle("Lỗi");
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    // ===================== CẦU HÒA / XIN THUA =====================

    @FXML
    private void onDrawButton() {
        if (isGameOver || myDrawPending || incomingDrawOffer || drawOffersLeft <= 0) return;
        app.sendMessage(new Message(MessageType.DRAW_OFFER, null));
        drawOffersLeft--;
        myDrawPending = true;
        updateDrawButtons();
        showDrawBanner("Đã gửi lời cầu hòa, chờ đối thủ trả lời...", false, 0);
    }

    @FXML
    private void onSurrenderButton() {
        if (isGameOver) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Đối thủ sẽ được tính thắng. Bạn vẫn ở lại phòng và có thể chơi tiếp ván mới.",
                ButtonType.YES, ButtonType.NO);
        alert.setTitle("Xin thua");
        alert.setHeaderText("Bạn chắc chắn muốn xin thua?");
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStyleClass().add("my-alert");
        URL cssFile = AlertController.class.getResource("/client/view/main.css");
        if (cssFile != null) dialogPane.getStylesheets().add(cssFile.toExternalForm());
        alert.showAndWait().ifPresent(r -> {
            if (r == ButtonType.YES && !isGameOver) {
                app.sendMessage(new Message(MessageType.SURRENDER, null));
            }
        });
    }

    /** Đối thủ xin hòa: hiện dải thông báo, KHÔNG chặn bàn bài, KHÔNG dừng đồng hồ. */
    public void handleDrawOffer(String fromName) {
        if (isGameOver) return;
        incomingDrawOffer = true;
        showDrawBanner(fromName + " đề nghị hòa", true, 0);
    }

    @FXML
    private void onDrawAccept() {
        if (!incomingDrawOffer) return;
        incomingDrawOffer = false;
        hideDrawBanner();
        app.sendMessage(new Message(MessageType.DRAW_RESPONSE, Boolean.TRUE));
    }

    @FXML
    private void onDrawDecline() {
        if (!incomingDrawOffer) return;
        incomingDrawOffer = false;
        hideDrawBanner();
        app.sendMessage(new Message(MessageType.DRAW_RESPONSE, Boolean.FALSE));
    }

    /** Lời cầu hòa của mình bị từ chối / hết hạn. */
    public void handleDrawRejected(String reason) {
        myDrawPending = false;
        updateDrawButtons();
        if (!isGameOver) showDrawBanner(reason, false, 2500);
    }

    private void updateDrawButtons() {
        if (btnDraw == null) return;
        btnDraw.setText(myDrawPending ? "Đang chờ..." : "Cầu hòa (còn " + drawOffersLeft + ")");
        btnDraw.setDisable(isGameOver || myDrawPending || drawOffersLeft <= 0);
        btnSurrender.setDisable(isGameOver);
    }

    private void showDrawBanner(String text, boolean withButtons, long autoHideMs) {
        if (drawBanner == null) return;
        if (bannerHideTimer != null) bannerHideTimer.stop();
        lblDrawBanner.setText(text);
        btnDrawAccept.setVisible(withButtons); btnDrawAccept.setManaged(withButtons);
        btnDrawDecline.setVisible(withButtons); btnDrawDecline.setManaged(withButtons);
        drawBanner.setVisible(true);
        drawBanner.setManaged(true);
        if (autoHideMs > 0) {
            bannerHideTimer = new PauseTransition(Duration.millis(autoHideMs));
            bannerHideTimer.setOnFinished(e -> { if (!incomingDrawOffer && !myDrawPending) hideDrawBanner(); });
            bannerHideTimer.play();
        }
    }

    private void hideDrawBanner() {
        if (drawBanner == null) return;
        drawBanner.setVisible(false);
        drawBanner.setManaged(false);
    }

    /** Đối thủ thoát / mất kết nối giữa trận: chỉ cho về sảnh, không có "Chơi tiếp". */
    private void showOpponentLeftDialog() {
        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.setTitle("KẾT THÚC TRẬN ĐẤU");
        alert.setHeaderText("ĐỐI THỦ ĐÃ THOÁT — BẠN THẮNG! 🎉");
        alert.setContentText("Đối thủ đã rời trận hoặc mất kết nối.\nBạn được tính thắng.");
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setMinWidth(460);
        dialogPane.getStyleClass().add("my-alert");
        URL cssFile = AlertController.class.getResource("/client/view/main.css");
        if (cssFile != null) {
            dialogPane.getStylesheets().add(cssFile.toExternalForm());
        }
        ButtonType homeButton = new ButtonType("Về sảnh chính", ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(homeButton);

        resultAlert = alert;
        alert.showAndWait();
        resultAlert = null;
        if (leavingRoom) return;
        leaveAfterGame();
    }

    private void showResultDialog(String title, String header) {
        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.setTitle(title);
        alert.setHeaderText(header);
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStyleClass().add("my-alert");
        URL cssFile = AlertController.class.getResource("/client/view/main.css");
        if (cssFile != null) {
            dialogPane.getStylesheets().add(cssFile.toExternalForm());
        }

        ButtonType continueBtn = new ButtonType("Chơi tiếp", ButtonBar.ButtonData.OK_DONE);
        ButtonType homeButton = new ButtonType("Về sảnh chính", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(continueBtn, homeButton);

        resultAlert = alert;
        Optional<ButtonType> response = alert.showAndWait();
        resultAlert = null;
        if (leavingRoom) return;   // đã bị đưa về sảnh trong lúc hộp thoại đang mở

        if (response.isPresent() && response.get() == continueBtn) {
            app.sendMessage(new Message(MessageType.PLAY_AGAIN_REQUEST, userPlayer));
            lblTurnIndicator.setText("ĐANG CHỜ ĐỐI THỦ...");
            lblTurnIndicator.setTextFill(Color.web("#7a6a40"));
        } else {
            leaveAfterGame();
        }
    }
}