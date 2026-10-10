package client.controller;

import client.service.ClientService;
import constants.MessageType;
import entity.Message;
import javafx.application.Platform;
import javafx.fxml.FXML;

import javafx.event.ActionEvent;
import javafx.scene.control.*;

import java.net.URL;

public class LoginController {

    private ClientService app;

    @FXML
    private TextField txtUsername;

    @FXML
    private PasswordField txtPassword;

    @FXML
    private Label statusLabel;

    @FXML
    public void handleLogin(ActionEvent event) {

        if (statusLabel != null) {
            statusLabel.setText("Đang đăng nhập...");
        }

        String user = txtUsername.getText().trim();
        String pass = txtPassword.getText().trim();

        if (user.isEmpty() || pass.isEmpty()) {
            System.out.println("Loi User");
            if (statusLabel != null) {
                statusLabel.setText("Vui lòng nhập đầy đủ thông tin!");
            }
            return;
        }

        if (app == null) {
            System.out.println("Loi chua ket noi");
            if (statusLabel != null) {
                statusLabel.setText("Lỗi: Chưa kết nối!");
            }
            return;
        }
        try {
            app.sendMessage(new Message(MessageType.LOGIN, new String[]{user, pass}));
            if (statusLabel != null) {
                statusLabel.setText("Đã gửi yêu cầu đăng nhập...");
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (statusLabel != null) {
                Platform.runLater(() -> {
                    statusLabel.setText("Lỗi: " + e.getMessage());

                });
            }
        }
    }

    @FXML
    private void handleGoToSignUp(ActionEvent event) {
        try {
            app.showSignUpUI();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void setClient(ClientService app) {
        this.app = app;
    }
}