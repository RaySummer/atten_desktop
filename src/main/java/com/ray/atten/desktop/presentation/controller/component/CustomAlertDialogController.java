package com.ray.atten.desktop.presentation.controller.component;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;

@Component
public class CustomAlertDialogController {

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

    private boolean confirmed = false;
    private Stage dialogStage;

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    /**
     * 初始化彈窗內容和類型
     *
     * @param message    顯示的消息
     * @param type       彈窗類型 (e.g., "INFO", "WARNING", "CONFIRM")
     * @param showCancel 是否顯示取消按鈕
     */
    public void initialize(String message, String type, boolean showCancel) {
        messageLabel.setText(message);
        cancelButton.setVisible(showCancel);
        cancelButton.setManaged(showCancel); // 隱藏時不佔用空間

        // 【核心邏輯】：如果沒有取消按鈕，讓整個 HBox 居右
        if (!showCancel) {
            buttonBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        } else {
            // 如果有取消按鈕，維持默認的對齊方式
            buttonBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT); // 保持原樣，但在 FXML 中確保對齊
        }

        // 根據類型設置圖標 (假設您在 /images/ 中準備了 info.png, warning.png, confirm.png)
        String iconPath = "/images/" + type.toLowerCase() + ".png";
        try {
            iconImageView.setImage(new Image(getClass().getResourceAsStream(iconPath)));
        } catch (Exception e) {
            System.err.println("Icon not found for type: " + type);
            // 可以設置一個默認圖標
        }
    }

    @FXML
    private void handleConfirm() {
        confirmed = true;
        dialogStage.close();
    }

    @FXML
    private void handleCancel() {
        confirmed = false;
        dialogStage.close();
    }

    public boolean isConfirmed() {
        return confirmed;
    }
}
