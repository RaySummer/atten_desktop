package com.ray.atten.desktop.presentation.controller.component;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;

@Component
public class DownloadProgressController {

    @FXML
    private VBox rootBox;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label statusLabel;

    // 用于记录拖拽坐标偏移
    private double xOffset = 0;
    private double yOffset = 0;

    @FXML
    public void initialize() {
        // 在组件初始化时设置拖拽逻辑
        setupDragEvents();
    }

    /**
     * 实现无边框窗口的拖拽功能
     */
    private void setupDragEvents() {
        // 鼠标按下时计算偏移量
        rootBox.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        // 鼠标拖动时更新窗口位置
        rootBox.setOnMouseDragged(event -> {
            Stage stage = (Stage) rootBox.getScene().getWindow();
            if (stage != null) {
                stage.setX(event.getScreenX() - xOffset);
                stage.setY(event.getScreenY() - yOffset);
            }
        });
    }

    public ProgressBar getProgressBar() {
        return progressBar;
    }

    public Label getStatusLabel() {
        return statusLabel;
    }
}