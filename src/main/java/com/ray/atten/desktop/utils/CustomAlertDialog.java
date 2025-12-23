package com.ray.atten.desktop.utils;

import com.ray.atten.desktop.presentation.controller.component.CustomAlertDialogController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
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

            // --- 核心优化：寻找父窗口并设置居中 ---
            // 自动寻找当前已显示的主窗口
            Window owner = Window.getWindows().stream()
                    .filter(Window::isShowing)
                    .findFirst()
                    .orElse(null);

            if (owner != null) {
                dialogStage.initOwner(owner);
            }

            if (StringUtils.isNotEmpty(title)) {
                dialogStage.setTitle(title);
            }

            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initStyle(StageStyle.TRANSPARENT);
            dialogStage.setResizable(false);

            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);

            // --- 动态应用主题 ---
            String theme = ConfigRepo.getTheme();
            if ("dark".equalsIgnoreCase(theme)) {
                root.getStyleClass().add("dark-mode");
            } else {
                root.getStyleClass().add("light-mode");
            }

            String cssPath = CustomAlertDialog.class.getResource("/css/dialog.css").toExternalForm();
            scene.getStylesheets().add(cssPath);

            dialogStage.setScene(scene);

            // 设置 Stage 和初始化数据
            controller.setDialogStage(dialogStage);
            controller.initialize(message, type.toString(), showCancel);

            // --- 核心优化：计算位置居中 ---
            // 必须在 setScene 之后，但在 show 之前或 Shown 事件中处理
            if (owner != null) {
                // 使用 setOnShown 确保在窗口尺寸计算完成后再定位
                dialogStage.setOnShown(event -> {
                    double x = owner.getX() + (owner.getWidth() - dialogStage.getWidth()) / 2;
                    double y = owner.getY() + (owner.getHeight() - dialogStage.getHeight()) / 2;
                    dialogStage.setX(x);
                    dialogStage.setY(y);
                });
            } else {
                dialogStage.centerOnScreen();
            }

            dialogStage.showAndWait();

            return controller.isConfirmed();

        } catch (IOException e) {
            e.printStackTrace();
            javafx.scene.control.Alert fallback = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            fallback.setContentText("自定義彈窗加載失敗: " + e.getMessage());
            fallback.showAndWait();
            return false;
        }
    }

    // ... 其他 showWarning, showError, showInfo, showConfirmation 方法保持不变 ...

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