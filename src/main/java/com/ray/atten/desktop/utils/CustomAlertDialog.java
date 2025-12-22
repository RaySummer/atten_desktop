package com.ray.atten.desktop.utils;

import com.ray.atten.desktop.presentation.controller.component.CustomAlertDialogController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;

public class CustomAlertDialog {

    public enum Type {
        INFO, WARNING, ERROR, CONFIRM
    }

    private static boolean showDialog(String title, String message, Type type, boolean showCancel) {
        try {
            FXMLLoader loader = new FXMLLoader(CustomAlertDialog.class.getResource("/view/component/CustomAlertDialogView.fxml"));
            Parent root = loader.load();

            CustomAlertDialogController controller = loader.getController();

            Stage dialogStage = new Stage();
            if (StringUtils.isNotEmpty(title)) {
                dialogStage.setTitle(title);
            }

            // 设置模态：必须处理完弹窗才能操作主界面
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            // 隐藏操作系统标题栏
            dialogStage.initStyle(StageStyle.TRANSPARENT);
            dialogStage.setResizable(false);

            Scene scene = new Scene(root);
            // 关键：Scene 背景设为透明，否则 CSS 的圆角外会有黑色背景
            scene.setFill(Color.TRANSPARENT);

            // --- 动态应用主题 ---
            String theme = ConfigRepo.getTheme(); // "dark" 或 "light"
            if ("dark".equalsIgnoreCase(theme)) {
                root.getStyleClass().add("dark-mode");
            } else {
                root.getStyleClass().add("light-mode");
            }

            // 加载 CSS
            String cssPath = CustomAlertDialog.class.getResource("/css/dialog.css").toExternalForm();
            scene.getStylesheets().add(cssPath);

            dialogStage.setScene(scene);

            // 先设置 Stage 再初始化拖拽逻辑
            controller.setDialogStage(dialogStage);
            controller.initialize(message, type.toString(), showCancel);

            dialogStage.showAndWait();

            return controller.isConfirmed();

        } catch (IOException e) {
            e.printStackTrace();
            // 回退到原生 Alert
            javafx.scene.control.Alert fallback = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            fallback.setContentText("自定義彈窗加載失敗: " + e.getMessage());
            fallback.showAndWait();
            return false;
        }
    }

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