package com.ray.atten.desktop.presentation.controller.component;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;

@Component
public class CustomAlertDialogController {

    @FXML
    private VBox dialogRoot;
    @FXML
    private ImageView iconImageView;
    @FXML
    private Label messageLabel;
    @FXML
    private Button confirmButton;
    @FXML
    private Button cancelButton;
    @FXML
    private HBox buttonBox;
    @FXML
    private StackPane iconContainer; // 注入容器

    private boolean confirmed = false;
    private Stage dialogStage;

    // 拖拽窗口用的坐标偏移
    private double xOffset = 0;
    private double yOffset = 0;

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
        setupDragEvents();
    }

    /**
     * 实现无边框窗口的拖拽功能
     */
    private void setupDragEvents() {
        dialogRoot.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        dialogRoot.setOnMouseDragged(event -> {
            if (dialogStage != null) {
                dialogStage.setX(event.getScreenX() - xOffset);
                dialogStage.setY(event.getScreenY() - yOffset);
            }
        });
    }

    /**
     * 初始化彈窗內容和類型
     *
     * @param message    顯示的消息
     * @param type       彈窗類型 (INFO, WARNING, ERROR, CONFIRM)
     * @param showCancel 是否顯示取消按鈕
     */
    public void initialize(String message, String type, boolean showCancel) {
        messageLabel.setText(message);
        cancelButton.setVisible(showCancel);
        cancelButton.setManaged(showCancel); // 隱藏時不佔用佈局空間

        // 统一对齐逻辑
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        // 1. 设置图片路径逻辑 (保持你之前的)
        String iconPath = "/images/" + type.toLowerCase() + ".png";
        try {
            var resource = getClass().getResource(iconPath);
            if (resource != null) {
                Image img = new Image(resource.toExternalForm());
                iconImageView.setImage(img);

                // 2. 【核心修复】创建裁剪区域防止尖角突出
                // 创建一个和图片一样大的矩形
                javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(100, 100);
                clip.setArcWidth(25);  // 圆角弧度，数值越大圆角越明显
                clip.setArcHeight(25);

                // 将矩形作为遮罩应用到 ImageView 上
                iconImageView.setClip(clip);

                // 3. 可选：给容器增加一点边框阴影效果，让圆角更平滑
                iconContainer.setStyle("-fx-background-radius: 12.5; " +
                        "-fx-border-radius: 12.5; " +
                        "-fx-border-color: #E0E0E0; " +
                        "-fx-border-width: 1;");
            }
        } catch (Exception e) {
            System.err.println("加载图标失败: " + e.getMessage());
        }
    }

    @FXML
    private void handleConfirm() {
        confirmed = true;
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    @FXML
    private void handleCancel() {
        confirmed = false;
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    public boolean isConfirmed() {
        return confirmed;
    }
}