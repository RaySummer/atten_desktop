package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.presentation.controller.component.DownloadProgressController;
import com.ray.atten.desktop.service.SysAppVersionService;
import com.ray.atten.desktop.utils.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Map;

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
    private Button btnAdminList;
    @FXML
    private Button btnCompany;
    @FXML
    private Button btnDevice;
    @FXML
    private Button btnSettings;
    @FXML
    private StackPane rootStackPane; // 注入根容器
    @FXML
    private Label lblAdminName;
    @FXML
    private VBox adminMenuSection;

    @Autowired
    private ConfigurableApplicationContext springContext;

    @Autowired
    private SysAppVersionService versionService;

    @Autowired
    private LoadingManager loadingManager;

    private double xOffset = 0;
    private double yOffset = 0;

    // --- 【新增】四边及四角拖拽缩放控制变量 ---
    private double mouseScreenX = 0;
    private double mouseScreenY = 0;
    private boolean isResizing = false;
    private int resizeMode = 0; // 0:无, 1:左, 2:右, 3:上, 4:下, 5:左上, 6:右上, 7:左下, 8:右下
    private static final double RESIZE_MARGIN = 8.0; // 边缘8像素响应范围

    @FXML
    public void initialize() {
        // 设置姓名
        lblAdminName.setText(SessionContext.getUsername());

        // 核心：根据登录时保存的角色决定是否显示管理菜单
        if (SessionContext.IsSuperAdmin()) {
            adminMenuSection.setVisible(true);
            adminMenuSection.setManaged(true);
        }

        // 将根容器交给管理器，管理器会加载 LoadingView 并放置在最上层
        loadingManager.init(rootStackPane);

        // 1. 实现无边框窗口的拖拽移动与四边缩放
        enableWindowDragAndResize();

        // 2. 处理主题应用和默认选中状态
        Platform.runLater(() -> {
            applySavedTheme();
            // 默认显示员工界面并高亮按钮
            showEmployeeView();
            checkUpdate();
        });
    }

    private void applySavedTheme() {
        String savedTheme = ConfigRepo.getTheme();
        String cssPath = savedTheme.equals("dark") ? "/css/dark.css" : "/css/light.css";
        Scene scene = mainContainer.getScene();

        if (scene != null && !scene.getStylesheets().contains(cssPath)) {
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

    /**
     * 【重构与升级】：同时支持“中间区域平移拖拽”与“四边/四角鼠标拉伸缩放”
     */
    private void enableWindowDragAndResize() {
        // A. 悬停在边缘时改变鼠标指针图标 (↔ ↕ ↖ ↗ 等)
        rootStackPane.setOnMouseMoved(e -> {
            Stage stage = (Stage) rootStackPane.getScene().getWindow();
            if (stage == null || stage.isMaximized()) {
                rootStackPane.setCursor(Cursor.DEFAULT);
                return;
            }

            double x = e.getX();
            double y = e.getY();
            double w = rootStackPane.getWidth();
            double h = rootStackPane.getHeight();

            boolean left = x < RESIZE_MARGIN;
            boolean right = x > w - RESIZE_MARGIN;
            boolean top = y < RESIZE_MARGIN;
            boolean bottom = y > h - RESIZE_MARGIN;

            if (left && top) rootStackPane.setCursor(Cursor.NW_RESIZE);
            else if (right && top) rootStackPane.setCursor(Cursor.NE_RESIZE);
            else if (left && bottom) rootStackPane.setCursor(Cursor.SW_RESIZE);
            else if (right && bottom) rootStackPane.setCursor(Cursor.SE_RESIZE);
            else if (left) rootStackPane.setCursor(Cursor.H_RESIZE);
            else if (right) rootStackPane.setCursor(Cursor.H_RESIZE);
            else if (top) rootStackPane.setCursor(Cursor.V_RESIZE);
            else if (bottom) rootStackPane.setCursor(Cursor.V_RESIZE);
            else rootStackPane.setCursor(Cursor.DEFAULT);
        });

        // B. 鼠标按下：判定是拉伸操作还是整体平移窗口操作
        rootStackPane.setOnMousePressed(event -> {
            Stage stage = (Stage) rootStackPane.getScene().getWindow();
            if (stage == null) return;

            double x = event.getX();
            double y = event.getY();
            double w = rootStackPane.getWidth();
            double h = rootStackPane.getHeight();

            boolean left = x < RESIZE_MARGIN;
            boolean right = x > w - RESIZE_MARGIN;
            boolean top = y < RESIZE_MARGIN;
            boolean bottom = y > h - RESIZE_MARGIN;

            isResizing = left || right || top || bottom;

            if (left && top) resizeMode = 5;
            else if (right && top) resizeMode = 6;
            else if (left && bottom) resizeMode = 7;
            else if (right && bottom) resizeMode = 8;
            else if (left) resizeMode = 1;
            else if (right) resizeMode = 2;
            else if (top) resizeMode = 3;
            else if (bottom) resizeMode = 4;
            else resizeMode = 0;

            if (isResizing) {
                mouseScreenX = event.getScreenX();
                mouseScreenY = event.getScreenY();
            } else {
                // 如果没有点在边缘，保留你原本的整体拖拽偏移记录
                xOffset = event.getSceneX();
                yOffset = event.getSceneY();
            }
        });

        // C. 鼠标拖动：执行缩放或平移
        rootStackPane.setOnMouseDragged(event -> {
            Stage stage = (Stage) rootStackPane.getScene().getWindow();
            if (stage == null || stage.isMaximized()) return;

            if (isResizing && resizeMode != 0) {
                // --- 执行四边与四角缩放逻辑 ---
                double deltaX = event.getScreenX() - mouseScreenX;
                double deltaY = event.getScreenY() - mouseScreenY;

                double oldX = stage.getX();
                double oldY = stage.getY();
                double oldW = stage.getWidth();
                double oldH = stage.getHeight();

                // 最小防碰撞保护，基于FXML中的minWidth/minHeight
                double minW = rootStackPane.getMinWidth() > 0 ? rootStackPane.getMinWidth() : 900.0;
                double minH = rootStackPane.getMinHeight() > 0 ? rootStackPane.getMinHeight() : 600.0;

                switch (resizeMode) {
                    case 1: // 左
                        if (oldW - deltaX >= minW) { stage.setX(oldX + deltaX); stage.setWidth(oldW - deltaX); }
                        break;
                    case 2: // 右
                        if (oldW + deltaX >= minW) stage.setWidth(oldW + deltaX);
                        break;
                    case 3: // 上
                        if (oldH - deltaY >= minH) { stage.setY(oldY + deltaY); stage.setHeight(oldH - deltaY); }
                        break;
                    case 4: // 下
                        if (oldH + deltaY >= minH) stage.setHeight(oldH + deltaY);
                        break;
                    case 5: // 左上
                        if (oldW - deltaX >= minW) { stage.setX(oldX + deltaX); stage.setWidth(oldW - deltaX); }
                        if (oldH - deltaY >= minH) { stage.setY(oldY + deltaY); stage.setHeight(oldH - deltaY); }
                        break;
                    case 6: // 右上
                        if (oldW + deltaX >= minW) stage.setWidth(oldW + deltaX);
                        if (oldH - deltaY >= minH) { stage.setY(oldY + deltaY); stage.setHeight(oldH - deltaY); }
                        break;
                    case 7: // 左下
                        if (oldW - deltaX >= minW) { stage.setX(oldX + deltaX); stage.setWidth(oldW - deltaX); }
                        if (oldH + deltaY >= minH) stage.setHeight(oldH + deltaY);
                        break;
                    case 8: // 右下
                        if (oldW + deltaX >= minW) stage.setWidth(oldW + deltaX);
                        if (oldH + deltaY >= minH) stage.setHeight(oldH + deltaY);
                        break;
                }

                mouseScreenX = event.getScreenX();
                mouseScreenY = event.getScreenY();
            } else {
                // --- 执行原有的窗口整体拖拽移动 ---
                stage.setX(event.getScreenX() - xOffset);
                stage.setY(event.getScreenY() - yOffset);
            }
        });

        // D. 鼠标松开：重置状态
        rootStackPane.setOnMouseReleased(e -> {
            isResizing = false;
            resizeMode = 0;
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
        Button[] navButtons = {btnEmployee, btnAttendance, btnDevice, btnSettings, btnAdminList, btnCompany};
        for (Button btn : navButtons) {
            if (btn != null) {
                btn.getStyleClass().remove("active");
            }
        }
        if (clickedButton != null) {
            clickedButton.getStyleClass().add("active");
        }
    }

    /**
     * 检查版本更新逻辑
     */
    public void checkUpdate() {
        Task<Map<String, Object>> task = new Task<>() {
            @Override
            protected Map<String, Object> call() throws Exception {
                return versionService.checkUpdateFromServer(AppConstants.CURRENT_VERSION);
            }
        };

        task.setOnSucceeded(event -> {
            Map<String, Object> data = task.getValue();
            if (data != null && (boolean) data.get("hasUpdate")) {
                String latestVersion = (String) data.get("latestVersion");
                String log = (String) data.get("updateLog");
                String downloadUrl = (String) data.get("downloadUrl");

                showCustomUpdateDialog(latestVersion, log, downloadUrl);
            }
        });

        task.setOnFailed(event -> {
            task.getException().printStackTrace();
        });

        new Thread(task).start();
    }

    /**
     * 弹出更新对话框
     */
    private void showCustomUpdateDialog(String version, String log, String relativeUrl) {
        String message = String.format("发现新版本 v%s\n\n更新日志：\n%s\n\n是否立即下载更新？", version, log);

        boolean confirmed = CustomAlertDialog.showConfirmation("系统更新", message);

        if (confirmed) {
            startDownloadTask(version, relativeUrl);
        }
    }

    private void startDownloadTask(String version, String relativeUrl) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/component/DownloadProgressView.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Parent root = loader.load();
            DownloadProgressController progressController = loader.getController();

            Stage progressStage = new Stage();
            Window owner = mainContainer.getScene().getWindow();
            if (owner != null) {
                progressStage.initOwner(owner);
            }
            progressStage.initModality(Modality.APPLICATION_MODAL);
            progressStage.initStyle(StageStyle.TRANSPARENT);
            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            progressStage.setScene(scene);
            progressStage.show();

            Task<File> downloadTask = versionService.createDownloadTask(relativeUrl);

            progressController.getProgressBar().progressProperty().bind(downloadTask.progressProperty());
            progressController.getStatusLabel().textProperty().bind(downloadTask.messageProperty());

            downloadTask.setOnSucceeded(e -> {
                Platform.runLater(() -> {
                    progressStage.close();
                    versionService.executeUpdaterScript(version);
                });
            });

            downloadTask.setOnFailed(e -> {
                Platform.runLater(() -> {
                    progressStage.close();
                    Throwable ex = downloadTask.getException();
                    CustomAlertDialog.showError("更新失败", "下载包损坏或网络超时");
                });
            });

            downloadTask.setOnCancelled(e -> {
                Platform.runLater(progressStage::close);
            });

            Thread thread = new Thread(downloadTask);
            thread.setDaemon(true);
            thread.start();

        } catch (IOException e) {
            CustomAlertDialog.showError("加载失败", "无法启动下载窗口");
        }
    }

    @FXML
    private void showAdminListView() {
        loadView("/view/AdminListView.fxml");
        updateActiveButton(btnAdminList);
    }

    @FXML
    private void showCompanyView() {
        loadView("/view/DeviceManagementView.fxml");
        updateActiveButton(btnCompany);
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        boolean confirm = CustomAlertDialog.showConfirmation("确认注销", "您确定要注销当前登录状态吗？");
        if (!confirm) return;

        SessionContext.logout();
        ConfigRepo.saveToken(null);

        try {
            Stage currentStage = (Stage) rootStackPane.getScene().getWindow();
            currentStage.close();

            Stage loginStage = new Stage();
            loginStage.initStyle(StageStyle.TRANSPARENT);

            ViewManager.switchView(loginStage, "/view/LoginView.fxml", springContext, "系统登录");

        } catch (Exception e) {
            e.printStackTrace();
            CustomAlertDialog.showError("错误", "注销失败，请重启程序");
        }
    }

    public static void switchView(Stage stage, String fxmlPath, ApplicationContext ctx, String title) throws Exception {
        FXMLLoader loader = new FXMLLoader(ViewManager.class.getResource(fxmlPath));
        loader.setControllerFactory(ctx::getBean);
        Parent root = loader.load();

        Scene scene = stage.getScene();
        if (scene == null) {
            scene = new Scene(root);
            stage.setScene(scene);
        } else {
            scene.setRoot(root);
        }

        scene.getStylesheets().clear();
        scene.getStylesheets().add(ViewManager.class.getResource("/css/style.css").toExternalForm());
        scene.getStylesheets().add(ViewManager.class.getResource("/css/login-style.css").toExternalForm());

        if (fxmlPath.contains("LoginView")) {
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        }

        stage.setTitle(title);
        stage.centerOnScreen();
        stage.show();
    }
}