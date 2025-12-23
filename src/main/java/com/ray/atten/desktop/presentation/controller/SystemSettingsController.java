package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.service.SysAppVersionService;
import com.ray.atten.desktop.utils.*;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SystemSettingsController {

    @FXML
    private Button btnLight;
    @FXML
    private Button btnDark;
    @FXML
    private Button btnCheckVersion;

    @Autowired
    private ApplicationContext springContext;

    @FXML
    private Label currentVersionLabel;
    @FXML
    private Label updateStatusLabel;

    @Autowired
    private SysAppVersionService versionService; // 直接使用 Service 检查更可控

    @Autowired
    private LoadingManager loadingManager;

    @FXML
    public void initialize() {
        // 在页签加载时显示当前版本号（对应 AppConstants 中定义的常量）
        if (currentVersionLabel != null) {
            currentVersionLabel.setText("v" + AppConstants.CURRENT_VERSION);
        }
        // 初始化时，根据当前配置决定哪个按钮高亮
        String currentTheme = ConfigRepo.getTheme();
        updateButtonHighlight(currentTheme);
    }

    @FXML
    public void setLightMode() {
        updateTheme("light");
    }

    @FXML
    public void setDarkMode() {
        updateTheme("dark");
    }

    private void updateTheme(String theme) {
        String cssPath = theme.equals("dark") ? "/css/dark.css" : "/css/light.css";
        Scene scene = btnLight.getScene();
        if (scene != null) {
            scene.getStylesheets().clear();
            scene.getStylesheets().add(getClass().getResource(cssPath).toExternalForm());

            // 1. 保存到磁盘
            ConfigRepo.saveTheme(theme);
            // 2. 刷新全局主题（如果有 WindowUtils 逻辑）
            WindowUtils.applyCurrentTheme(scene);

            // 3. 更新按钮的高亮状态
            updateButtonHighlight(theme);
        }
    }

    /**
     * 版本页签：点击“立即检查更新”
     */
    @FXML
    private void handleManualUpdate() {
        // 1. 界面反馈：禁用按钮，显示正在加载
        btnCheckVersion.setDisable(true);
        updateStatusLabel.setText("正在连接服务器...");
        updateStatusLabel.setStyle("-fx-text-fill: #2196F3;"); // 设为蓝色提示

        // 显示全屏居中的加载动画
        loadingManager.show("正在检查版本信息...");

        // 2. 创建检查任务
        Task<Map<String, Object>> task = new Task<>() {
            @Override
            protected Map<String, Object> call() throws Exception {
                // 调用服务层获取数据
                return versionService.checkUpdateFromServer(AppConstants.CURRENT_VERSION);
            }
        };

        task.setOnSucceeded(event -> {
            loadingManager.hide();
            btnCheckVersion.setDisable(false);
            Map<String, Object> data = task.getValue();

            if (data != null && (boolean) data.get("hasUpdate")) {
                updateStatusLabel.setText("发现新版本！");
                updateStatusLabel.setStyle("-fx-text-fill: #4CAF50;"); // 绿色提示

                // 3. 发现更新，通过 MainController 弹出下载对话框
                MainController mainController = springContext.getBean(MainController.class);
                String latestVersion = (String) data.get("latestVersion");
                String log = (String) data.get("updateLog");
                String downloadUrl = (String) data.get("downloadUrl");

                // 这里需要确保 MainController 的 checkUpdate() 逻辑或显示弹窗的方法是公有的
                mainController.checkUpdate();
            } else {
                // 4. 没有更新
                updateStatusLabel.setText("当前已是最新版本");
                updateStatusLabel.setStyle("-fx-text-fill: #7ece78;"); // 灰色提示
                CustomAlertDialog.showInfo("版本检查", "您当前使用的已是最新版本，无需更新。");
            }
        });

        task.setOnFailed(event -> {
            loadingManager.hide();
            btnCheckVersion.setDisable(false);
            updateStatusLabel.setText("检查更新失败");
            updateStatusLabel.setStyle("-fx-text-fill: #F44336;"); // 红色提示

            Throwable ex = task.getException();
            CustomAlertDialog.showError("检查失败", "无法获取更新信息：" + ex.getMessage());
        });

        new Thread(task).start();
    }

    /**
     * 统一管理按钮的高亮样式类
     */
    private void updateButtonHighlight(String theme) {
        // 先移除所有按钮的 active 类
        btnLight.getStyleClass().remove("active");
        btnDark.getStyleClass().remove("active");

        // 根据当前主题添加 active 类
        if ("light".equals(theme)) {
            btnLight.getStyleClass().add("active");
        } else {
            btnDark.getStyleClass().add("active");
        }
    }
}