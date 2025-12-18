package com.ray.atten.desktop.utils;

import com.ray.atten.desktop.presentation.controller.component.CustomAlertDialogController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;

public class CustomAlertDialog {

    // 彈窗類型枚舉 (可擴展)
    public enum Type {
        INFO, WARNING, ERROR, CONFIRM
    }

    /**
     * 顯示一個模態提示窗口
     *
     * @param title      窗口標題
     * @param message    顯示的消息
     * @param type       消息類型
     * @param showCancel 是否顯示取消按鈕 (用於 CONFIRM)
     * @return 如果是 CONFIRM 類型，返回 true 表示用戶點擊了確定
     */
    private static boolean showDialog(String title, String message, Type type, boolean showCancel) {
        try {
            // 注意：這裡我們假設這個工具類不需要 Spring 注入 Controller
            FXMLLoader loader = new FXMLLoader(CustomAlertDialog.class.getResource("/view/component/CustomAlertDialogView.fxml"));
            Parent root = loader.load();

            CustomAlertDialogController controller = loader.getController();

            Stage dialogStage = new Stage();
            dialogStage.setTitle(title);
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initStyle(StageStyle.UTILITY); // 隱藏操作系統的標題欄按鈕，顯得更簡潔
            dialogStage.setResizable(false);

            Scene scene = new Scene(root);
            // 這裡可以設置 CSS
            String cssPath = CustomAlertDialog.class.getResource("/css/dialog.css").toExternalForm();
            scene.getStylesheets().add(cssPath);

            dialogStage.setScene(scene);

            controller.setDialogStage(dialogStage);
            controller.initialize(message, type.toString(), showCancel);

            dialogStage.showAndWait();

            return controller.isConfirmed();

        } catch (IOException e) {
            e.printStackTrace();
            // 如果加載失敗，退回到原生 Alert
            new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR, "自定義彈窗加載失敗: " + e.getMessage()).showAndWait();
            return false;
        }
    }

    // --- 靜態調用方法 ---

    public static void showWarning(String title, String message) {
        showDialog(title, message, Type.WARNING, false);
    }

    public static void showError(String title, String message) {
        showDialog(title, message, Type.ERROR, false);
    }

    public static void showInfo(String title, String message) {
        showDialog(title, message, Type.INFO, false);
    }

    public static boolean showConfirmation(String title, String message) {
        return showDialog(title, message, Type.CONFIRM, true);
    }

}
