package com.ray.atten.desktop.presentation.controller.component;

import javafx.animation.FadeTransition;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class ShowZoomImageWindow {

    public static void showZoomWindow(Image img) {
        if (img == null) return;

        // 1. 获取屏幕尺寸以实现全屏遮罩效果
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double screenWidth = screenBounds.getWidth();
        double screenHeight = screenBounds.getHeight();

        Stage zoomStage = new Stage();
        zoomStage.initStyle(StageStyle.TRANSPARENT);
        zoomStage.initModality(Modality.APPLICATION_MODAL);

        // 2. 配置图片及其缩放检测
        ImageView imageView = new ImageView(img);
        imageView.setPreserveRatio(true);

        // 限制图片最大为屏幕的 85%，留出边缘点击空间
        double maxW = screenWidth * 0.85;
        double maxH = screenHeight * 0.85;
        double scale = Math.min(1.0, Math.min(maxW / img.getWidth(), maxH / img.getHeight()));

        imageView.setFitWidth(img.getWidth() * scale);
        imageView.setFitHeight(img.getHeight() * scale);

        // 3. 构建容器：rootPane 铺满全屏
        StackPane rootPane = new StackPane(imageView);
        // 背景设为半透明黑，突出图片，且点击此背景即可关闭
        rootPane.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7);");

        Scene scene = new Scene(rootPane, screenWidth, screenHeight);
        scene.setFill(Color.TRANSPARENT);
        zoomStage.setScene(scene);

        // 4. 淡入动画 (Fade In)
        rootPane.setOpacity(0); // 初始透明度为0
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), rootPane);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        // 5. 点击关闭逻辑 (点击全屏任意位置触发淡出)
        rootPane.setOnMouseClicked(e -> {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(200), rootPane);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(event -> zoomStage.close()); // 动画结束后真正关闭窗口
            fadeOut.play();
        });

        // 窗口定位
        zoomStage.setX(screenBounds.getMinX());
        zoomStage.setY(screenBounds.getMinY());
        zoomStage.show();
    }

}
