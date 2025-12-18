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

//    @FXML
//    public void setLightMode() {
//        applyTheme("/css/light.css");
//        saveThemePreference("light");
//        // UI 反馈
//        btnLight.getStyleClass().add("theme-button-selected");
//        btnDark.getStyleClass().remove("theme-button-selected");
//
//        // 设置按钮的视觉状态（JDK 8 手法）
//        btnLight.setStyle("-fx-border-color: #2196F3; -fx-border-width: 2;");
//        btnDark.setStyle("-fx-border-color: transparent;");
//    }
//
//    @FXML
//    public void setDarkMode() {
//        applyTheme("/css/dark.css");
//        saveThemePreference("dark");
//        btnDark.getStyleClass().add("theme-button-selected");
//        btnLight.getStyleClass().remove("theme-button-selected");
//
//        btnDark.setStyle("-fx-border-color: #bb86fc; -fx-border-width: 2;");
//        btnLight.setStyle("-fx-border-color: transparent;");
//    }
//
//    private void applyTheme(String cssPath) {
//        // 1. 获取当前节点的 Scene (注意：btnLight 必须已经在界面上加载)
//        Scene scene = btnLight.getScene();
//        if (scene != null) {
//            // 2. 清除所有样式表
//            scene.getStylesheets().clear();
//            // 3. 添加新样式表
//            String css = getClass().getResource(cssPath).toExternalForm();
//            scene.getStylesheets().add(css);
//        } else {
//            System.err.println("切换失败：无法获取当前 Scene");
//        }
//    }

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
        Scene scene = btnLight.getScene(); // 随便拿个本页面的组件获取 Scene
        if (scene != null) {
            scene.getStylesheets().clear();
            scene.getStylesheets().add(getClass().getResource(cssPath).toExternalForm());

            // 调用 ConfigRepo 保存到磁盘
            ConfigRepo.saveTheme(theme);
            // 2. 刷新当前主页面的样式
            WindowUtils.applyCurrentTheme(btnLight.getScene());
            // 刷新按钮边框反馈
            btnLight.setStyle(theme.equals("light") ? "-fx-border-color: #2196F3; -fx-border-width: 2;" : "-fx-border-color: transparent;");
            btnDark.setStyle(theme.equals("dark") ? "-fx-border-color: #bb86fc; -fx-border-width: 2;" : "-fx-border-color: transparent;");
        }
    }

}