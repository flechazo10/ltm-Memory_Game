package client.controller;

import client.service.ClientService;
import entity.DetailMatch;
import entity.Match;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class HistoryDetailController {
    private ClientService app;

    @FXML private Label lblPlayerName;
    @FXML private Label lblTotalMatches;
    @FXML private Label lblWins;
    @FXML private Label lblLosses;
    @FXML private Label lblDraws;

    @FXML private TableView<DetailMatch> tblDetailHistory;
    @FXML private TableColumn<DetailMatch, Integer> colMatchId;
    @FXML private TableColumn<DetailMatch, String> colOpponent;
    @FXML private TableColumn<DetailMatch, String> colResult;
    @FXML private TableColumn<DetailMatch, Double> colPoints; // Đổi sang Double
    @FXML private TableColumn<DetailMatch, String> colStartTime;
    @FXML private TableColumn<DetailMatch, String> colEndTime;
    @FXML private TableColumn<DetailMatch, String> colDuration;
    @FXML private TableColumn<DetailMatch, String> colDate;
    @FXML private TableColumn<DetailMatch, String> colIsQuit;

    public void setClient(ClientService app) {
        this.app = app;
    }

    @FXML
    private void initialize() {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        colMatchId.setCellValueFactory(cellData ->
                new SimpleIntegerProperty(
                        cellData.getValue().getMatch() != null ? cellData.getValue().getMatch().getId() : 0
                ).asObject()
        );

        colOpponent.setCellValueFactory(cellData ->
                new SimpleStringProperty(
                        cellData.getValue().getPlayer() != null ? cellData.getValue().getPlayer().getUsername() : ""
                )
        );

        colStartTime.setCellValueFactory(cellData -> {
            Match match = cellData.getValue().getMatch();
            return new SimpleStringProperty((match != null && match.getStartTime() != null) ? match.getStartTime().format(timeFormatter) : "");
        });

        colEndTime.setCellValueFactory(cellData -> {
            Match match = cellData.getValue().getMatch();
            return new SimpleStringProperty((match != null && match.getEndTime() != null) ? match.getEndTime().format(timeFormatter) : "");
        });

        colDate.setCellValueFactory(cellData -> {
            Match match = cellData.getValue().getMatch();
            return new SimpleStringProperty((match != null && match.getStartTime() != null) ? match.getStartTime().format(dateFormatter) : "");
        });

        colDuration.setCellValueFactory(cellData -> {
            Match match = cellData.getValue().getMatch();
            if (match == null || match.getStartTime() == null) return new SimpleStringProperty("");
            LocalDateTime start = match.getStartTime();
            LocalDateTime end = match.getEndTime();
            if (end != null) {
                Duration duration = Duration.between(start, end);
                return new SimpleStringProperty(String.format("%02d:%02d", duration.toMinutes(), duration.getSeconds() % 60));
            }
            return new SimpleStringProperty("Đang đấu");
        });

        // WIN / LOSE / DRAW -> Thắng / Thua / Hòa, có màu (style trong ranking.css)
        colResult.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().getResult() != null ? cellData.getValue().getResult().toString() : ""));
        colResult.setCellFactory(column -> new TableCell<DetailMatch, String>() {
            @Override
            protected void updateItem(String result, boolean empty) {
                super.updateItem(result, empty);
                getStyleClass().removeAll("result-win", "result-lose", "result-draw");
                if (empty || result == null || result.isEmpty()) {
                    setText(null);
                    return;
                }
                switch (result.toUpperCase()) {
                    case "WIN" -> { setText("Thắng"); getStyleClass().add("result-win"); }
                    case "LOSE" -> { setText("Thua"); getStyleClass().add("result-lose"); }
                    case "DRAW" -> { setText("Hòa"); getStyleClass().add("result-draw"); }
                    default -> setText(result);
                }
            }
        });

        // Số cặp đã ăn trong trận (GameRoom lưu vào points)
        colPoints.setCellValueFactory(cellData ->
                new SimpleDoubleProperty(cellData.getValue().getPoints()).asObject()
        );
        colPoints.setCellFactory(column -> new TableCell<DetailMatch, Double>() {
            @Override
            protected void updateItem(Double points, boolean empty) {
                super.updateItem(points, empty);
                if (empty || points == null) {
                    setText(null);
                } else {
                    setText(points == Math.floor(points) ? String.valueOf(points.longValue()) : String.valueOf(points));
                }
            }
        });

        colIsQuit.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().isQuit() ? "Thoát" : "")
        );
    }

    public void setHistoryDetail(List<DetailMatch> list, String playerName) {
        if (list != null) {
            tblDetailHistory.getItems().setAll(list);
            lblPlayerName.setText("Người chơi: " + playerName);

            int total = list.size();
            long wins = list.stream().filter(m -> m.getResult() != null && m.getResult().toString().equalsIgnoreCase("WIN")).count();
            long losses = list.stream().filter(m -> m.getResult() != null && m.getResult().toString().equalsIgnoreCase("LOSE")).count();
            long draws = list.stream().filter(m -> m.getResult() != null && m.getResult().toString().equalsIgnoreCase("DRAW")).count();

            lblTotalMatches.setText(String.valueOf(total));
            lblWins.setText(String.valueOf(wins));
            lblLosses.setText(String.valueOf(losses));
            lblDraws.setText(String.valueOf(draws));
        }
    }

    @FXML
    private void onLogout() {
        if (app != null) {
            app.backFromHistoryDetail();
        }
    }
}