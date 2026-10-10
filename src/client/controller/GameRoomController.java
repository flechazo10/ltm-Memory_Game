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

    // Khớp hoàn toàn với GameRoom: 4x6 (24 lá), 15 giây/lượt
    private static final int GRID_ROWS = 4;
    private static final int GRID_COLS = 6;
    private static final int CARD_SIZE = 90;
    private static final int TURN_TIME_SECONDS = 15;
    private static final String BACK_CARD_IMAGE_PATH = "/assets/images/background/back_card.jpg";
    private String currentBackCardImagePath = BACK_CARD_IMAGE_PATH;

    private static final Map<String, String> THEME_BACK_CARDS = Map.of(
            "kimesu", "/assets/images/background/back_card.jpg",
            "onepunchman", "/assets/images/background/opm.jpg",
            "footballer", "/assets/images/background/ball.jpg",
            "badminton", "/assets/images/background/qua.jpg",
            "sakamoto", "/assets/images/background/back_card.jpg",
            "Default", BACK_CARD_IMAGE_PATH
    );
    private static final Map<String, String> THEME_BACKGROUNDS = Map.of(
            "kimesu", "/assets/images/background/Kimetsu.jpg",
            "onepunchman", "/assets/images/background/back_ground_opm.jpg",
            "footballer", "/assets/images/background/sta.jpg",
            "badminton", "/assets/images/background/caulong.jpg",
            "sakamoto", "/assets/images/background/sakamoto.jpg",
            "Default", "/assets/images/background/Kimetsu.jpg"
    );

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

    private boolean isMusicOn;
    private Timeline turnTimeline;

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
        });
    }

    private void setupMusic() {
        try {
            if (isMusicOn) {
                String pathPlay = getClass().getResource("/assets/images/background/btn_on_music.png").toString();
                btnMusic.setStyle("-fx-background-image: url('" + pathPlay + "'); -fx-background-size: cover; -fx-background-radius: 50%; -fx-background-repeat: no-repeat; -fx-background-position: center;");
            } else {
                String pathPause = getClass().getResource("/assets/images/background/btn_off_music.png").toString();
                btnMusic.setStyle("-fx-background-image: url('" + pathPause + "'); -fx-background-size: cover; -fx-background-radius: 50%; -fx-background-repeat: no-repeat; -fx-background-position: center;");
            }
        } catch (Exception e) {
            System.err.println("Lỗi tải nhạc: " + e.getMessage());
        }
    }

    private void changeBackgroundByTheme(String themeName) {
        if (background == null) return;
        String imagePath = THEME_BACKGROUNDS.getOrDefault(themeName, THEME_BACKGROUNDS.get("Default"));
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
        this.currentBackCardImagePath = THEME_BACK_CARDS.getOrDefault(themeName, BACK_CARD_IMAGE_PATH);
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
                if (theme != null) {
                    changeBackgroundByTheme(theme);
                    changeBackCardByTheme(theme);
                }
                updateTurnState(firstTurnPlayerId);
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
            updateTurnState(currentPlayerId);
            resetCardSelection();
        });
    }

    public void handleCardsMatched(Object[] matchData) {
        Platform.runLater(() -> {
            int card1Index = (int) matchData[0];
            int card2Index = (int) matchData[1];
            Player matchedPlayer = (Player) matchData[2];
            int p1Score = (int) matchData[3];
            int p2Score = (int) matchData[4];
            String scoredColor = (String) matchData[5]; // Viền Đỏ (RED) hoặc Xanh (BLUE) từ Server

            processMatchAnimation(card1Index, card2Index, scoredColor);
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
        plusLabel.setStyle("-fx-text-fill: #00FF00; -fx-font-size: 30px; -fx-font-weight: bold;");

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
            int card1Index = (int) unmatchData[0];
            int card2Index = (int) unmatchData[1];

            PauseTransition pause = new PauseTransition(Duration.millis(800));
            pause.setOnFinished(e -> {
                flipCardBackAnimation(card1Index);
                flipCardBackAnimation(card2Index);
                resetCardSelection();
            });
            pause.play();
        });
    }

    public void handleGameEnd(Map<String, Object> resultData) {
        Platform.runLater(() -> {
            isGameOver = true;
            if (turnTimeline != null) turnTimeline.stop();
            tblCard.setDisable(true);

            int winnerId = (int) resultData.get("winnerId");
            String text;
            if (winnerId == -1) {
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
        Platform.runLater(this::resetCardSelection);
    }

    private void handleCardClick(int index, Button cardButton) {
        if (!isMyTurn || isProcessingMove || isGameOver || matchedCardIndices.contains(index) || (firstCardIndex != null && firstCardIndex == index)) {
            return;
        }

        isProcessingMove = true;

        if (firstCardIndex == null) {
            firstCardIndex = index;
            firstCardButton = cardButton;
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
            findCardButton(idx1).ifPresent(btn -> flipCardToShow(btn, idx1, null));
            findCardButton(idx2).ifPresent(btn -> flipCardToShow(btn, idx2, null));
        });
    }

    private Optional<Button> findCardButton(int index) {
        return findNodeInGrid(tblCard, index).map(node -> (Button) node);
    }

    private void resetGameState() {
        isMyTurn = false;
        isProcessingMove = false;
        isGameOver = false;
        matchedCardIndices.clear();
        resetCardSelection();
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
        lblTurnIndicator.setTextFill(isMyTurn ? Color.web("#39FF14") : Color.web("#FF3131"));

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
        timeBar.setStyle("-fx-accent: #3d9496;");

        turnTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            remainingTime[0]--;
            timeLabel.setText(remainingTime[0] + "s");
            timeBar.setProgress((double) remainingTime[0] / TURN_TIME_SECONDS);

            if (remainingTime[0] <= 3) {
                timeBar.setStyle("-fx-accent: red;");
            } else if (remainingTime[0] <= 6) {
                timeBar.setStyle("-fx-accent: orange;");
            }

            if (remainingTime[0] <= 0) {
                turnTimeline.stop();
                timeLabel.setText("Hết giờ");
            }
        }));
        turnTimeline.setCycleCount(TURN_TIME_SECONDS);
        turnTimeline.play();
    }

    private void createGameBoard() {
        tblCard.getChildren().clear();
        double delay = 0;
        for (int i = 0; i < GRID_ROWS * GRID_COLS; i++) {
            int row = i / GRID_COLS;
            int col = i % GRID_COLS;
            Button cardButton = createCardButton(i);
            tblCard.add(cardButton, col, row);
            dealCardEffect(cardButton, delay);
            delay += 20;
        }
    }

    private Button createCardButton(int index) {
        Button card = new Button();
        card.setPrefSize(CARD_SIZE, CARD_SIZE);
        card.setStyle("-fx-padding: 0; -fx-background-color: transparent; -fx-cursor: hand;");
        card.setUserData(index);

        ImageView imageView = createImageViewFromPath(currentBackCardImagePath);
        card.setGraphic(imageView);
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
        createFlipAnimation(card, frontView, onFinished).play();
    }

    private void flipCardBackAnimation(int index) {
        findCardButton(index).ifPresent(card -> {
            ImageView backView = createImageViewFromPath(currentBackCardImagePath);
            createFlipAnimation(card, backView, null).play();
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
        countdownLabel.setTextFill(Color.LAVENDER);

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
            goHome();
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
        if (isMusicOn) {
            app.setMusicOn(false);
            btnMusic.setStyle("-fx-background-image: url('/assets/images/background/btn_off_music.png'); -fx-background-size: cover; -fx-background-radius: 50%; -fx-background-repeat: no-repeat; -fx-background-position: center;");
        } else {
            app.setMusicOn(true);
            btnMusic.setStyle("-fx-background-image: url('/assets/images/background/btn_on_music.png'); -fx-background-size: cover; -fx-background-radius: 50%; -fx-background-repeat: no-repeat; -fx-background-position: center;");
        }
    }

    private ImageView createImageViewFromBase64(String base64) {
        try {
            String base64Data = base64.substring(base64.indexOf(",") + 1);
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            Image image = new Image(new ByteArrayInputStream(imageBytes));
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(CARD_SIZE - 10);
            imageView.setFitHeight(CARD_SIZE - 10);
            imageView.setPreserveRatio(true);
            return imageView;
        } catch (Exception e) {
            return createImageViewFromPath(null);
        }
    }

    private ImageView createImageViewFromPath(String path) {
        ImageView imageView = new ImageView();
        try {
            Image image = new Image(Objects.requireNonNull(getClass().getResourceAsStream(path != null ? path : currentBackCardImagePath)));
            imageView.setImage(image);
            imageView.setFitWidth(CARD_SIZE - 10);
            imageView.setFitHeight(CARD_SIZE - 10);
            imageView.setPreserveRatio(true);
        } catch (Exception e) {
            System.err.println("Lỗi tải ảnh: " + path);
        }
        return imageView;
    }

    private Optional<Node> findNodeInGrid(GridPane gridPane, int index) {
        int col = index % GRID_COLS;
        int row = index / GRID_COLS;
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

        alert.showAndWait().ifPresent(response -> {
            if (response == continueBtn) {
                app.sendMessage(new Message(MessageType.PLAY_AGAIN_REQUEST, userPlayer));
            } else {
                goHome();
            }
        });
    }
}