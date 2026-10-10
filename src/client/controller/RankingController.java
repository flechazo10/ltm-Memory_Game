package client.controller;

import client.service.ClientService;
import entity.Player;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
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
    @FXML private TableColumn<Player, String> colMedalR;

    public void setClient(ClientService client) {
        this.client = client;
    }

    @FXML
    private void initialize() {
        colRankR.setCellValueFactory(new PropertyValueFactory<>("rank"));
        colNameR.setCellValueFactory(new PropertyValueFactory<>("username"));
        colTotalScoreR.setCellValueFactory(new PropertyValueFactory<>("totalScore"));

        colMedalR.setCellFactory(column -> new TableCell<Player, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setText(null);
                    setStyle("");
                } else {
                    int rank = getTableView().getItems().get(getIndex()).getRank();
                    switch (rank) {
                        case 1 -> {
                            setText("🥇");
                            setStyle("-fx-text-fill: gold; -fx-font-size: 20px;");
                        }
                        case 2 -> {
                            setText("🥈");
                            setStyle("-fx-text-fill: silver; -fx-font-size: 20px;");
                        }
                        case 3 -> {
                            setText("🥉");
                            setStyle("-fx-text-fill: #cd7f32; -fx-font-size: 20px;");
                        }
                        default -> {
                            setText("_");
                            setStyle("-fx-text-fill: #CCCCCC; -fx-font-size: 16px;");
                        }
                    }
                    setAlignment(Pos.CENTER);
                }
            }
        });
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