package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.utils.ConfigRepo;
import com.ray.atten.desktop.utils.WindowUtils;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import org.springframework.stereotype.Component;

@Component
public class SystemSettingsController {

    @FXML
    private Button btnLight;
    @FXML
    private Button btnDark;

    @FXML
    public void initialize() {
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