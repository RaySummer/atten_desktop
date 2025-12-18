package com.ray.atten.desktop.utils;

import javafx.scene.Scene;

import java.util.Objects;

public class WindowUtils {

    /**
     * 为指定的 Scene 应用当前系统保存的主题
     */
    public static void applyCurrentTheme(Scene scene) {
        if (scene == null) return;

        // 1. 获取当前主题 (light 或 dark)
        String theme = ConfigRepo.getTheme();

        // 2. 构造 CSS 路径
        String cssPath = "/css/" + ("dark".equals(theme) ? "dark.css" : "light.css");

        try {
            // 3. 清除旧样式并添加新样式
            scene.getStylesheets().clear();
            scene.getStylesheets().add(
                    Objects.requireNonNull(WindowUtils.class.getResource(cssPath)).toExternalForm()
            );
        } catch (Exception e) {
            System.err.println("主题加载失败，请检查 CSS 文件路径: " + cssPath);
            e.printStackTrace();
        }
    }

}
