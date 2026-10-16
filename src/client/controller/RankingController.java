package client.controller;

import client.service.ClientService;
import entity.Player;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;

public class RankingController {
    private ClientService client;

    @FXML private TableView<Player> tblRanking;
    @FXML private TableColumn<Player, Integer> colRankR;
    @FXML private TableColumn<Player, String> colNameR;
    @FXML private TableColumn<Player, Double> colTotalScoreR; // Đổi sang Double
    @FXML private TableColumn<Player, Integer> colWinCountR;
    @FXML private TableColumn<Player, String> colMedalR;

    public void setClient(ClientService client) {
        this.client = client;
    }

    @FXML
    private void initialize() {
        colRankR.setCellValueFactory(new PropertyValueFactory<>("rank"));
        colNameR.setCellValueFactory(new PropertyValueFactory<>("username"));
        colTotalScoreR.setCellValueFactory(new PropertyValueFactory<>("totalScore"));
        colWinCountR.setCellValueFactory(new PropertyValueFactory<>("winCount"));

        colTotalScoreR.setCellFactory(column -> new TableCell<Player, Double>() {
            @Override
            protected void updateItem(Double score, boolean empty) {
                super.updateItem(score, empty);
                setText(empty || score == null ? null : formatScore(score));
            }
        });

        // Huy hiệu tròn vàng / bạc / đồng cho top 3 (style trong ranking.css)
        colMedalR.setCellFactory(column -> new TableCell<Player, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(null);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    return;
                }
                int rank = getTableView().getItems().get(getIndex()).getRank();
                if (rank >= 1 && rank <= 3) {
                    Label medal = new Label(String.valueOf(rank));
                    medal.getStyleClass().addAll("medal", "medal-" + rank);
                    setGraphic(medal);
                }
                setAlignment(Pos.CENTER);
            }
        });
    }

    /** 7.0 -> "7", 7.5 -> "7.5" */
    private static String formatScore(double score) {
        return score == Math.floor(score) ? String.valueOf((long) score) : String.valueOf(score);
    }

    public void setRankingList(List<Player> list) {
        if (list != null) {
            tblRanking.getItems().setAll(list);
        }
    }

    @FXML
    private void onBack() {
        if (client != null) {
            client.showMainUI();
        }
    }
}