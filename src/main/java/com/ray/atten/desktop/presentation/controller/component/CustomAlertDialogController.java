package com.ray.atten.desktop.presentation.controller.component;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.var;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class CustomAlertDialogController {

    @FXML private VBox dialogRoot;
    @FXML private ImageView iconImageView;
    @FXML private Label messageLabel;
    @FXML private Button confirmButton;
    @FXML private Button cancelButton;
    @FXML private HBox buttonBox;

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

        // 設置圖標逻辑优化
        String iconFileName = type.toLowerCase() + ".png";
        String iconPath = "/images/" + iconFileName;
        try {
            // 使用更健壮的资源读取方式
            var resource = getClass().getResource(iconPath);
            if (resource != null) {
                iconImageView.setImage(new Image(resource.toExternalForm()));
            } else {
                System.err.println("找不到图标资源: " + iconPath);
            }
        } catch (Exception e) {
            System.err.println("加载图标失败: " + type + ", 错误: " + e.getMessage());
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