package client.controller;

import client.service.ClientService;
import constants.MessageType;
import constants.Status;
import entity.Message;
import entity.Player;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;

public class HistoryController {

    @FXML private Label lblWelcome;
    @FXML private TableView<Player> tblHistory;
    @FXML private TableColumn<Player, String> colNameH;
    @FXML private TableColumn<Player, Double> colTotalScoreH; // Đổi sang Double
    @FXML private TableColumn<Player, String> colStatusH;

    private ClientService client;

    @FXML
    private void initialize() {
        colNameH.setCellValueFactory(new PropertyValueFactory<>("username"));
        colTotalScoreH.setCellValueFactory(new PropertyValueFactory<>("totalScore"));
        colStatusH.setCellValueFactory(new PropertyValueFactory<>("status"));

        colStatusH.setCellFactory(column -> new TableCell<Player, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                } else {
                    status = status.toUpperCase();
                    if (status.equals(String.valueOf(Status.ONLINE))) {
                        setText("Online");
                        setStyle("-fx-text-fill: #00FF99; -fx-font-weight: bold;");
                    } else if (status.equals(String.valueOf(Status.OFFLINE))) {
                        setText("Offline");
                        setStyle("-fx-text-fill: #AAAAAA; -fx-font-weight: normal;");
                    } else {
                        setText("In Game");
                        setStyle("-fx-text-fill: #efd967; -fx-font-weight: bold;");
                    }
                    setAlignment(Pos.CENTER);
                }
            }
        });

        tblHistory.setOnMouseClicked(event -> {
            if (event.getClickCount() == 1) {
                onDetailHistory();
            }
        });
    }

    public void setClient(ClientService client) {
        this.client = client;
    }

    @FXML
    private void onDetailHistory() {
        Player selected = tblHistory.getSelectionModel().getSelectedItem();
        if (selected != null && client != null) {
            client.sendMessage(new Message(MessageType.GET_HISTORY_DETAIL, selected.getId()));
            client.setSelectedPlayerName(selected.getUsername());
        }
    }

    public void setHistoryList(List<Player> list) {
        if (list != null) {
            tblHistory.getItems().setAll(list);
        }
    }

    @FXML
    private void onBack() {
        if (client != null) {
            client.showMainUI();
        }
    }

    public void setWelcomeText(String text) {
        lblWelcome.setText(text);
    }
}