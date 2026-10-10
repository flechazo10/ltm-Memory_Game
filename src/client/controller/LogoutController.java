package client.controller;

import client.service.ClientService;
import constants.MessageType;
import entity.Message;
import entity.Player;
import javafx.fxml.FXML;
import javafx.stage.Stage;

import java.awt.event.ActionEvent;
import java.io.IOException;

public class LogoutController {
    private Stage modalStage;
    private ClientService clientService;
    private MainController mainController;
    private Player currentPlayer;

    @FXML
    public void setModalStage(Stage modalStage) {
        this.modalStage = modalStage;
    }

    public void setClientService(ClientService clientService) {
        this.clientService = clientService;
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    public void setCurrentPlayer(Player currentPlayer) {
        this.currentPlayer = currentPlayer;
    }

    @FXML
    private void handleLogout() throws IOException {
        clientService.sendMessage(new Message(MessageType.LOGOUT, currentPlayer));
        System.out.println("Logout request sent for player: " + currentPlayer.getUsername());
        modalStage.close();
    }

    @FXML
    private void handleCancelLogout() {
        modalStage.close();
    }
}
