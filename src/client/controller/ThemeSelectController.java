package client.controller;

import client.service.ClientService;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.net.URL;

/** Màn chọn chủ đề kiểu "chọn màn chơi": 1 chủ đề ở giữa, 2 chủ đề bên cạnh mờ. */
public class ThemeSelectController {
    private ClientService app;
    private int index = 0;
    private double dragStartX;

    @FXML private BorderPane root;
    @FXML private Label lblThemeName;
    @FXML private Label lblDifficulty;
    @FXML private ImageView imgMain;
    @FXML private ImageView imgPrev;
    @FXML private ImageView imgNext;
    @FXML private Label lblPrev;
    @FXML private Label lblNext;
    @FXML private StackPane centerPane;
    @FXML private VBox leftSide;
    @FXML private VBox rightSide;
    @FXML private HBox dots;

    @FXML
    private void initialize() {
        Rectangle clip = new Rectangle(300, 300);   // bo góc ảnh chính
        clip.setArcWidth(28);
        clip.setArcHeight(28);
        imgMain.setClip(clip);

        // Kéo chuột sang trái/phải để đổi chủ đề
        root.setOnMousePressed(e -> dragStartX = e.getSceneX());
        root.setOnMouseReleased(e -> {
            double dx = e.getSceneX() - dragStartX;
            if (dx > 60) move(-1);
            else if (dx < -60) move(1);
        });
        leftSide.setOnMouseClicked(e -> move(-1));
        rightSide.setOnMouseClicked(e -> move(1));
    }

    public void setClient(ClientService app, Scene scene) {
        this.app = app;
        String current = app.getSelectedTheme();
        for (int i = 0; i < ClientService.THEMES.length; i++) {
            if (ClientService.THEMES[i][0].equals(current)) index = i;
        }
        lblDifficulty.setText("Độ khó: " + ClientService.difficultyLabel(app.getSelectedDifficulty()));
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.LEFT) { move(-1); e.consume(); }
            else if (e.getCode() == KeyCode.RIGHT) { move(1); e.consume(); }
        });
        show(0);
    }

    @FXML private void onPrev() { move(-1); }
    @FXML private void onNext() { move(1); }

    @FXML
    private void onBack() {
        app.showMenuUI();
    }

    @FXML
    private void onEnter() {
        app.setSelectedTheme(ClientService.THEMES[index][0]);
        app.showMainUI();
    }

    private void move(int step) {
        int n = ClientService.THEMES.length;
        index = ((index + step) % n + n) % n;
        show(step);
    }

    private void show(int direction) {
        int n = ClientService.THEMES.length;
        String[] cur = ClientService.THEMES[index];
        String[] prev = ClientService.THEMES[(index - 1 + n) % n];
        String[] next = ClientService.THEMES[(index + 1) % n];

        lblThemeName.setText(cur[1]);
        setSquareImage(imgMain, cur[0]);
        setSquareImage(imgPrev, prev[0]);
        setSquareImage(imgNext, next[0]);
        lblPrev.setText(prev[1]);
        lblNext.setText(next[1]);
        boolean many = n > 1;
        leftSide.setVisible(many);
        rightSide.setVisible(many);

        dots.getChildren().clear();
        for (int i = 0; i < n; i++) {
            Region dot = new Region();
            dot.getStyleClass().add("theme-dot");
            if (i == index) dot.getStyleClass().add("theme-dot-active");
            dots.getChildren().add(dot);
        }

        if (direction != 0) {   // hiệu ứng trượt + hiện dần
            TranslateTransition slide = new TranslateTransition(Duration.millis(220), centerPane);
            slide.setFromX(direction * 80);
            slide.setToX(0);
            FadeTransition fade = new FadeTransition(Duration.millis(220), centerPane);
            fade.setFromValue(0.2);
            fade.setToValue(1);
            new ParallelTransition(slide, fade).play();
        }
    }

    /** Ảnh đại diện chủ đề = img01.jpg, cắt lấy phần vuông ở giữa để không bị méo. */
    private void setSquareImage(ImageView view, String themeKey) {
        URL url = getClass().getResource("/assets/images/" + themeKey + "/img01.jpg");
        if (url == null) {
            view.setImage(null);
            return;
        }
        Image img = new Image(url.toExternalForm(), 600, 600, true, true);
        double w = img.getWidth(), h = img.getHeight(), s = Math.min(w, h);
        view.setImage(img);
        view.setViewport(new Rectangle2D((w - s) / 2, (h - s) / 2, s, s));
    }
}
