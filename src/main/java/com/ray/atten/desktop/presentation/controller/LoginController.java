package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.service.AuthService;
import com.ray.atten.desktop.utils.CredentialManager; // 需新建此工具类
import com.ray.atten.desktop.utils.CustomAlertDialog;
import com.ray.atten.desktop.utils.ViewManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class LoginController {
    @FXML
    private TextField txtUsername;
    @FXML
    private PasswordField txtPassword;
    @FXML
    private CheckBox chkRememberMe; // 对应 FXML 中的新 CheckBox
    @FXML
    private Button btnLogin;

    @Autowired
    private AuthService authService;
    @Autowired
    private ApplicationContext springContext;

    private double xOffset = 0;
    private double yOffset = 0;

    @FXML
    public void initialize() {
        // 初始化时尝试加载保存的凭据
        CredentialManager.LoginInfo savedInfo = CredentialManager.loadCredentials();
        if (savedInfo.isRememberMe()) {
            txtUsername.setText(savedInfo.getUsername());
            txtPassword.setText(savedInfo.getPassword());
            chkRememberMe.setSelected(true);

            // 延时聚焦到登录按钮，方便直接回车
            Platform.runLater(() -> btnLogin.requestFocus());
        }
    }

    /**
     * 鼠标按下时记录初始坐标
     */
    @FXML
    private void handleMousePressed(MouseEvent event) {
        xOffset = event.getSceneX();
        yOffset = event.getSceneY();
    }

    /**
     * 鼠标拖拽时计算偏移并移动窗口
     */
    @FXML
    private void handleMouseDragged(MouseEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setX(event.getScreenX() - xOffset);
        stage.setY(event.getScreenY() - yOffset);
    }

    /**
     * 退出系统图标点击事件
     */
    @FXML
    private void handleExit() {
        Platform.runLater(() -> {
            System.exit(0);
        });
    }

    /**
     * 处理回车键逻辑
     * 请在 FXML 根节点 VBox 上添加 onKeyPressed="#handleKeyPressed"
     */
    @FXML
    private void handleKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER && !btnLogin.isDisable()) {
            executeLoginLogic();
        }
    }

    @FXML
    public void onLoginAction(ActionEvent event) {
        executeLoginLogic();
    }

    private void executeLoginLogic() {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText();
        boolean rememberMe = chkRememberMe.isSelected();

        if (username.isEmpty() || password.isEmpty()) {
            CustomAlertDialog.showWarning("提示", "请输入用户名和密码");
            return;
        }

        btnLogin.setDisable(true);

        Task<Boolean> loginTask = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                return authService.login(username, password);
            }
        };

        loginTask.setOnSucceeded(e -> {
            if (loginTask.getValue()) {
                // 【核心逻辑】登录成功后保存或清除凭据
                CredentialManager.saveCredentials(username, password, rememberMe);
                switchToMain();
            } else {
                btnLogin.setDisable(false);
                CustomAlertDialog.showError("失败", "登录失效，请联系管理员");
            }
        });

        loginTask.setOnFailed(e -> {
            btnLogin.setDisable(false);
            Throwable ex = loginTask.getException();
            log.error("登录异常", ex);
            CustomAlertDialog.showError("登录错误", ex.getMessage());
        });

        new Thread(loginTask).start();
    }

    @FXML
    private void handleBackToSetup(ActionEvent event) {
        Stage stage = (Stage) txtUsername.getScene().getWindow();
        ViewManager.switchView(stage, "/view/ServerSetupView.fxml", springContext, "服务器配置");
    }

    private void switchToMain() {
        try {
            Stage stage = (Stage) txtUsername.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/MainView.fxml"));
            loader.setControllerFactory(springContext::getBean);

            Parent root = loader.load();
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.centerOnScreen();
        } catch (IOException e) {
            log.error("跳转主页失败", e);
        }
    }
}