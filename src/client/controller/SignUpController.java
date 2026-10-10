package client.controller;

import client.service.ClientService;
import constants.MessageType;
import entity.Message;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;

import java.net.URL;

public class SignUpController {

    @FXML
    private TextField txtUsername;

    @FXML
    private PasswordField txtPassword;

    @FXML
    private PasswordField txtConfirmPassword;

    @FXML
    private Label lblError;

    @FXML
    private Label lblSuccess;

    private ClientService clientService;

    public void setClient(ClientService clientService) {
        this.clientService = clientService;
    }

    @FXML
    private void handleSignUp() {
        hideMessages();

        String username = txtUsername.getText().trim();
        String password = txtPassword.getText();
        String confirmPassword = txtConfirmPassword.getText();

        if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showError("Vui lòng điền đầy đủ thông tin!");
            return;
        }

        if (username.length() < 3) {
            showError("Tên đăng nhập phải có ít nhất 3 ký tự!");
            return;
        }

        if (password.length() < 1) {
            showError("Mật khẩu phải có ít nhất 1 ký tự!");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError("Mật khẩu xác nhận không khớp!");
            return;
        }

        try {
            String[] credentials = {username, password};
            Message signupMsg = new Message(MessageType.SIGN_UP, credentials);
            clientService.sendMessage(signupMsg);
            System.out.println("Đã gửi yêu cầu đăng ký: " + username);
        } catch (Exception e) {
            showError("Lỗi kết nối với server!");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleBackToLogin() {
        clientService.showLoginUI();
    }

    public void showError(String message) {
        lblError.setText(message);
        lblError.setVisible(true);
        lblError.setManaged(true);
        lblSuccess.setVisible(false);
        lblSuccess.setManaged(false);
    }

    private void hideMessages() {
        lblError.setVisible(false);
        lblError.setManaged(false);
        lblSuccess.setVisible(false);
        lblSuccess.setManaged(false);
    }
}