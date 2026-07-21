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
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

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

    private double xOffset = 0;
    private double yOffset = 0;

    // --- 定义系统的默认配置值 ---
    private static final String DEFAULT_HOST = "172.16.0.234";
    private static final String DEFAULT_PORT = "8821";

    @FXML
    public void initialize() {
        protocolCombo.getItems().addAll("http://", "https://");

        // 1. 从磁盘加载并回显数据
        Properties props = ConfigRepo.loadConfig();
        String savedProtocol = props.getProperty("server.protocol");
        String savedHost = props.getProperty("server.host");
        String savedPort = props.getProperty("server.port");

        if (savedProtocol != null) {
            protocolCombo.setValue(savedProtocol);
        } else {
            protocolCombo.getSelectionModel().select(0); // 默认 http
        }

        // --- 【核心修改一】：根据回显状态设置文本与初始灰色样式 ---
        if (savedHost != null && !savedHost.isEmpty()) {
            hostField.setText(savedHost);
            // 有保存的值，说明是用户输入过的，给黑色样式
            updateFieldStyle(hostField, false);
        } else {
            // 没有保存的值，直接填入默认IP，并给灰色样式
            hostField.setText(DEFAULT_HOST);
            updateFieldStyle(hostField, true);
        }

        if (savedPort != null && !savedPort.isEmpty()) {
            portField.setText(savedPort);
            updateFieldStyle(portField, false);
        } else {
            // 没有保存的值，填入你需要的默认端口 8821，并给灰色样式
            portField.setText(DEFAULT_PORT);
            updateFieldStyle(portField, true);
        }

        // --- 【核心修改二】：给两个输入框配置动态颜色监听器 ---
        setupTextListener(hostField, DEFAULT_HOST);
        setupTextListener(portField, DEFAULT_PORT);

        // --- 【核心修改三】：贴心优化，当用户点击默认值时自动全选，方便直接打字覆盖 ---
        setupFocusListener(hostField, DEFAULT_HOST);
        setupFocusListener(portField, DEFAULT_PORT);
    }

    /**
     * 提取的通用文本监听：用户打字或删空时自动切换灰色/黑色
     */
    private void setupTextListener(TextField field, String defaultValue) {
        field.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.equals(defaultValue) || newValue.isEmpty()) {
                // 如果变回了默认值，或者是空的，显示灰色
                updateFieldStyle(field, true);
            } else {
                // 如果输入了不一样的值，显示黑色
                updateFieldStyle(field, false);
            }
        });
    }

    /**
     * 提取的通用焦点监听：获得焦点时如果是默认值就全选
     */
    private void setupFocusListener(TextField field, String defaultValue) {
        field.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal && field.getText().equals(defaultValue)) {
                Platform.runLater(field::selectAll);
            }
        });
    }

    /**
     * 动态切换 JavaFX 的文本框 CSS 颜色属性
     */
    private void updateFieldStyle(TextField field, boolean isDefault) {
        // 1. 先清除我們自訂的這兩個主題關聯樣式，防止重複疊加
        field.getStyleClass().removeAll("server-default-value", "server-custom-value");

        // 2. 根據狀態，只新增對應的類別名稱
        if (isDefault) {
            field.getStyleClass().add("server-default-value");
        } else {
            field.getStyleClass().add("server-custom-value");
        }
    }

    /**
     * 鼠标按下：记录初始位置
     */
    @FXML
    private void handleMousePressed(MouseEvent event) {
        xOffset = event.getSceneX();
        yOffset = event.getSceneY();
    }

    /**
     * 鼠标拖拽：移动窗口
     */
    @FXML
    private void handleMouseDragged(MouseEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setX(event.getScreenX() - xOffset);
        stage.setY(event.getScreenY() - yOffset);
    }

    /**
     * 退出系统
     */
    @FXML
    private void handleExit() {
        Platform.runLater(() -> {
            System.exit(0);
        });
    }

    @FXML
    public void onConnect() {
        final String protocol = protocolCombo.getValue();

        // --- 【核心修改四】：如果用户直接清空了输入框，则在提交时智能顶上默认值 ---
        String hostInput = hostField.getText().trim();
        final String host = hostInput.isEmpty() ? DEFAULT_HOST : hostInput;

        String portInput = portField.getText().trim();
        final String portText = portInput.isEmpty() ? DEFAULT_PORT : portInput;

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
                String cleanProtocol = protocol.replace("://", "");
                return NetworkUtil.checkConnection(cleanProtocol, host, port);
            }
        };

        checkTask.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                Boolean success = checkTask.getValue();
                if (success != null && success) {
                    if (checkTask.getValue()) {
                        // 1. 更新内存
                        AppConstants.updateApiBaseUrl(protocol, host, portText);
                        // 2. 写入磁盘
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
        thread.setDaemon(true);
        thread.start();
    }

    private void switchToMain() {
        try {
            Stage stage = (Stage) protocolCombo.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/LoginView.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Parent root = loader.load();
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("加载主界面失败: " + e.getMessage());
        }
    }
}