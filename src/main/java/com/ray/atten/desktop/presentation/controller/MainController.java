package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.presentation.controller.component.CustomAlertDialogController;
import com.ray.atten.desktop.presentation.controller.component.DownloadProgressController;
import com.ray.atten.desktop.service.SysAppVersionService;
import com.ray.atten.desktop.utils.AppConstants;
import com.ray.atten.desktop.utils.ConfigRepo;
import com.ray.atten.desktop.utils.CustomAlertDialog;
import com.ray.atten.desktop.utils.LoadingManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.springframework.beans.factory.annotation.Autowired;
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
    private Button btnDevice;
    @FXML
    private Button btnSettings;
    @FXML
    private StackPane rootStackPane; // 注入根容器

    @Autowired
    private ConfigurableApplicationContext springContext;

    @Autowired
    private SysAppVersionService versionService;

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
            checkUpdate();
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
            // 可以记录日志或弹窗提示网络异常
            task.getException().printStackTrace();
        });

        new Thread(task).start();
    }

    /**
     * 弹出更新对话框
     */
    private void showCustomUpdateDialog(String version, String log, String relativeUrl) {
        // 构造显示的消息内容
        String message = String.format("发现新版本 v%s\n\n更新日志：\n%s\n\n是否立即下载更新？", version, log);

        // 使用你封装的自定义弹窗类
        // 标题可以根据需要传入，或者传 null 使用默认
        boolean confirmed = CustomAlertDialog.showConfirmation("系统更新", message);

        // 用户点击了“确认”按钮
        if (confirmed) {
            startDownloadTask(version, relativeUrl);
        }
    }

    private void startDownloadTask(String version, String relativeUrl) {
        try {
            // 1. 加载弹窗
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/component/DownloadProgressView.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Parent root = loader.load();
            DownloadProgressController progressController = loader.getController();

            Stage progressStage = new Stage();
            // --- 核心修复：设置父窗口并计算位置 ---
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

            // 2. 获取 Task
            Task<File> downloadTask = versionService.createDownloadTask(relativeUrl);

            // 3. 核心修复：双向绑定 (确保进度条会动)
            // 注意：必须在 UI 线程绑定
            progressController.getProgressBar().progressProperty().bind(downloadTask.progressProperty());
            progressController.getStatusLabel().textProperty().bind(downloadTask.messageProperty());

            // 4. 下载成功监听
            downloadTask.setOnSucceeded(e -> {
                Platform.runLater(() -> {
                    progressStage.close(); // 确保关闭
                    versionService.executeUpdaterScript(version); // 启动脚本
                });
            });

            // 5. 下载失败监听
            downloadTask.setOnFailed(e -> {
                Platform.runLater(() -> {
                    progressStage.close(); // 确保关闭
                    Throwable ex = downloadTask.getException();
                    CustomAlertDialog.showError("更新失败", "下载包损坏或网络超时: " + ex.getMessage());
                });
            });

            // 6. 下载取消监听（可选）
            downloadTask.setOnCancelled(e -> {
                Platform.runLater(progressStage::close);
            });

            // 启动线程
            Thread thread = new Thread(downloadTask);
            thread.setDaemon(true);
            thread.start();

        } catch (IOException e) {
            CustomAlertDialog.showError("加载失败", "无法启动下载窗口");
        }
    }
}