package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.utils.AppConstants;
import com.ray.atten.desktop.utils.ConfigRepo;
import com.ray.atten.desktop.utils.NetworkUtil;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

@Component
public class ServerSetupController {

    @Autowired
    private ApplicationContext springContext;

    @FXML
    private ComboBox<String> protocolCombo;
    @FXML
    private TextField hostField;
    @FXML
    private TextField portField;
    @FXML
    private Label statusLabel;

    @FXML
    public void initialize() {
        protocolCombo.getItems().addAll("http://", "https://");

        // --- 新增：从磁盘加载并回显数据 ---
        Properties props = ConfigRepo.loadConfig();
        String savedProtocol = props.getProperty("server.protocol");
        String savedHost = props.getProperty("server.host");
        String savedPort = props.getProperty("server.port");

        if (savedProtocol != null) {
            protocolCombo.setValue(savedProtocol);
        } else {
            protocolCombo.getSelectionModel().select(0); // 默认 http
        }

        if (savedHost != null) {
            hostField.setText(savedHost);
        }

        if (savedPort != null) {
            portField.setText(savedPort);
        } else {
            portField.setText("80");
        }
    }

    @FXML
    public void onConnect() {
        // 获取界面输入
        final String protocol = protocolCombo.getValue();
        final String host = hostField.getText().trim();
        final String portText = portField.getText().trim();

        // 1. 校验逻辑
        if (host.isEmpty() || !NetworkUtil.isValidHost(host)) {
            statusLabel.setText("提示：请输入有效的 IP 或域名");
            return;
        }

        final int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            statusLabel.setText("提示：端口必须是数字");
            return;
        }

        statusLabel.setText("正在尝试连接服务器...");

        // 2. 异步任务
        Task<Boolean> checkTask = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                // 注意：checkConnection 内部必须处理好异常，不要直接崩掉
                String cleanProtocol = protocol.replace("://", "");
                return NetworkUtil.checkConnection(cleanProtocol, host, port);
            }
        };

        // 使用显式的 EventHandler 替代 Lambda（如果在 JDK 8 下运行不稳定）
        checkTask.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                Boolean success = checkTask.getValue();
                if (success != null && success) {
//                    // 更新全局常量
                    if (checkTask.getValue()) {
                        // 1. 更新内存
                        AppConstants.updateApiBaseUrl(protocol, host, portText);

                        // 2. 写入磁盘（进阶建议的部分，现在补全）
                        ConfigRepo.saveConfig(protocol, host, portText, "dark");

                        // 3. 跳转
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                switchToMain();
                            }
                        });
                    }
                } else {
                    statusLabel.setText("错误：无法连接到服务器，请检查地址、端口及防火墙");
                }
            }
        });

        checkTask.setOnFailed(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                Throwable e = checkTask.getException();
                statusLabel.setText("检测出错: " + (e != null ? e.getMessage() : "未知错误"));
            }
        });

        Thread thread = new Thread(checkTask);
        thread.setDaemon(true); // 设置为守护线程
        thread.start();
    }

    private void switchToMain() {
        try {
            // 只要是当前窗口的一个控件即可，这里假设用 protocolCombo
            Stage stage = (Stage) protocolCombo.getScene().getWindow();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/LoginView.fxml"));
            // 这里的 springContext 需要在 Controller 中 @Autowired 注入
            loader.setControllerFactory(springContext::getBean);

            Parent root = loader.load();
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.centerOnScreen(); // 切换后居中显示
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("加载主界面失败: " + e.getMessage());
        }
    }

}