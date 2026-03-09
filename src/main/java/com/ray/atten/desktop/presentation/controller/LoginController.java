package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.service.AuthService;
import com.ray.atten.desktop.utils.ConfigRepo;
import com.ray.atten.desktop.utils.CustomAlertDialog;
import com.ray.atten.desktop.utils.SessionContext;
import com.ray.atten.desktop.utils.ViewManager;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
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
    private Button btnLogin;

    @Autowired
    private AuthService authService; // 注入业务 Service
    @Autowired
    private ApplicationContext springContext; // 注入 Spring 上下文

    @FXML
    public void onLoginAction(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText();

        if (username.isEmpty() || password.isEmpty()) {
            CustomAlertDialog.showWarning("提示", "请输入用户名和密码");
            return;
        }

        // 界面反馈：禁用按钮防止重复点击
        btnLogin.setDisable(true);

        // 使用 Task 处理耗时操作，避免 UI 假死
        Task<Boolean> loginTask = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                return authService.login(username, password);
            }
        };

        loginTask.setOnSucceeded(e -> {
            if (loginTask.getValue()) {
                // 登录成功，跳转主页
                jumpToMain(event);
            } else {
                btnLogin.setDisable(false);
                CustomAlertDialog.showError("失败", "登录失效，请联系管理员");
            }
        });

        loginTask.setOnFailed(e -> {
            btnLogin.setDisable(false);
            Throwable ex = loginTask.getException();
            CustomAlertDialog.showError("登录错误", ex.getMessage());
        });

        new Thread(loginTask).start();
    }

    @FXML
    private void handleBackToSetup(ActionEvent event) {
        Stage stage = (Stage) txtUsername.getScene().getWindow();
        ViewManager.switchView(stage, "/view/ServerSetupView.fxml", springContext, "服务器配置");
    }

    private void jumpToMain(ActionEvent event) {
        switchToMain();
    }


    private void switchToMain() {
        try {
            // 只要是当前窗口的一个控件即可，这里假设用 protocolCombo
            Stage stage = (Stage) txtUsername.getScene().getWindow();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/MainView.fxml"));
            // 这里的 springContext 需要在 Controller 中 @Autowired 注入
            loader.setControllerFactory(springContext::getBean);

            Parent root = loader.load();
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.centerOnScreen(); // 切换后居中显示
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}