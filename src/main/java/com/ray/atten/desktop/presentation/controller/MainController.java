package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.utils.ConfigRepo;
import com.ray.atten.desktop.utils.CustomAlertDialog;
import com.ray.atten.desktop.utils.LoadingManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class MainController {

    @FXML
    private StackPane contentArea;
    @FXML
    private BorderPane mainContainer;
    @FXML
    private Button btnEmployee;
    @FXML
    private Button btnAttendance;
    @FXML
    private Button btnDevice;
    @FXML
    private Button btnSettings;
    @FXML
    private StackPane rootStackPane; // 注入根容器

    @Autowired
    private ConfigurableApplicationContext springContext;

    @Autowired
    private LoadingManager loadingManager;

    private double xOffset = 0;
    private double yOffset = 0;

    @FXML
    public void initialize() {
        // 将根容器交给管理器，管理器会加载 LoadingView 并放置在最上层
        loadingManager.init(rootStackPane);
        // 1. 实现拖拽
        enableWindowDrag();

        // 2. 处理主题应用和默认选中状态
        Platform.runLater(() -> {
            applySavedTheme();
            // 默认显示员工界面并高亮按钮
            showEmployeeView();
        });
    }

    private void applySavedTheme() {
        String savedTheme = ConfigRepo.getTheme();
        String cssPath = savedTheme.equals("dark") ? "/css/dark.css" : "/css/light.css";
        Scene scene = mainContainer.getScene();
//        if (scene != null) {
//            scene.getStylesheets().clear();
//            scene.getStylesheets().add(getClass().getResource(cssPath).toExternalForm());
//        }
        // 建议先添加新样式，再移除旧样式，防止样式中断导致的计算错误
        if (!scene.getStylesheets().contains(cssPath)) {
            scene.getStylesheets().add(cssPath);
            // 移除除了新加的这个以外的所有样式表
            scene.getStylesheets().removeIf(s -> !s.equals(cssPath));
        }
    }

    private void loadView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            loader.setControllerFactory(springContext::getBean);
            Parent view = loader.load();
            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void enableWindowDrag() {
        mainContainer.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        mainContainer.setOnMouseDragged(event -> {
            Stage stage = (Stage) mainContainer.getScene().getWindow();
            if (stage != null) {
                stage.setX(event.getScreenX() - xOffset);
                stage.setY(event.getScreenY() - yOffset);
            }
        });
    }

    // --- 视图切换方法（均已添加 updateActiveButton 调用） ---

    @FXML
    public void showEmployeeView() {
        loadView("/view/EmployeeListView.fxml");
        updateActiveButton(btnEmployee);
    }

    @FXML
    public void showAttendanceView() {
        loadView("/view/AttendanceView.fxml");
        updateActiveButton(btnAttendance);
    }

    @FXML
    public void showDeviceView() {
        loadView("/view/DeviceManagementView.fxml");
        updateActiveButton(btnDevice);
    }

    @FXML
    public void showSettingView() {
        loadView("/view/SystemSettingsView.fxml");
        updateActiveButton(btnSettings);
    }

    @FXML
    private void handleExit() {
        if (CustomAlertDialog.showConfirmation("确认退出", "您确定要退出考勤系统吗？")) {
            Stage stage = (Stage) mainContainer.getScene().getWindow();
            if (stage != null) stage.close();
            System.exit(0);
        }
    }

    /**
     * 统一管理按钮的高亮状态
     */
    private void updateActiveButton(Button clickedButton) {
        Button[] navButtons = {btnEmployee, btnAttendance, btnDevice, btnSettings};
        for (Button btn : navButtons) {
            if (btn != null) {
                btn.getStyleClass().remove("active");
            }
        }
        if (clickedButton != null) {
            clickedButton.getStyleClass().add("active");
        }
    }
}