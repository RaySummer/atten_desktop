package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.dto.FingerprintResult;
import com.ray.atten.desktop.dto.SyncRequest;
import com.ray.atten.desktop.model.AttendanceGroup;
import com.ray.atten.desktop.model.OaEmployee;
import com.ray.atten.desktop.presentation.controller.component.ShowZoomImageWindow;
import com.ray.atten.desktop.service.OaEmployeeService;
import com.ray.atten.desktop.utils.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.controlsfx.control.CheckComboBox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
public class EmployeeDetailController {

    @FXML
    private ImageView photoImageView;
    @FXML
    private ImageView fingerprint1ImageView;
    @FXML
    private ImageView fingerprint1ImageView1;

    @FXML
    private Label pinLabel, nameLabel, deptLabel, officeLocationLabel, entryDateLabel, statusLabel, lblMessage;
    @FXML
    private Button captureButton, uploadButton, enrollButton, reconnectButton, verifyButton, confirmButton, cancelButton, syncButton;

    @FXML
    private StackPane deviceGroupSelectorContainer;
    private CheckComboBox<String> deviceGroupCheckComboBox;
    private final ObservableList<String> deviceGroups = FXCollections.observableArrayList();

    private List<AttendanceGroup> allAttendanceGroups = new ArrayList<>();

    @Autowired
    private OaEmployeeService oaEmployeeService; // 注入 Service

    private Image currentPhoto;
    private String currentFingerprint1Base64;
    private OaEmployee employee;
    private volatile boolean isConnecting = false;
    private FingerprintResult capturedResult;

    private ScheduledExecutorService heartbeatExecutor;

    @Autowired
    private ApplicationContext springContext;
    @Autowired
    private LoadingManager loadingManager;

    private final Image DEFAULT_AVATAR = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/default_avatar.png")));
    private final Image DEFAULT_FINGERPRINT = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/default_fingerprint.png")));
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Runnable onCloseRequest;

    public void setOnCloseRequest(Runnable onCloseRequest) {
        this.onCloseRequest = onCloseRequest;
    }

    @FXML
    public void initialize() {
        // 1. 立即设置默认图，防止 ImageView 渲染异常
        if (photoImageView != null) photoImageView.setImage(DEFAULT_AVATAR);
        if (fingerprint1ImageView != null) fingerprint1ImageView.setImage(DEFAULT_FINGERPRINT);

        // 2. 隐藏重连按钮，设置初始消息
        if (reconnectButton != null) reconnectButton.setVisible(false);
        if (lblMessage != null) lblMessage.setText("正在加载配置...");

        // 3. 必须先初始化容器内的控件，再加载数据
        initDeviceGroupSelector();

        // 4. 将网络/数据库请求放在 Platform.runLater 中，确保 UI 已经完全展示
        Platform.runLater(() -> {
            loadAttendanceGroups();
            tryConnectDevice(false);
        });
    }

    /**
     * 初始化 CheckComboBox 并注入到 StackPane 容器
     */
    private void initDeviceGroupSelector() {
        if (deviceGroupSelectorContainer == null) return;

        deviceGroupCheckComboBox = new CheckComboBox<>(deviceGroups);
        deviceGroupCheckComboBox.setPrefWidth(180.0);
        deviceGroupCheckComboBox.setMaxWidth(Double.MAX_VALUE);

        // 设置初始标题
        deviceGroupCheckComboBox.setTitle("请选择设备组");

        // 【新增】监听选中项变化，动态更新标题回显
        deviceGroupCheckComboBox.getCheckModel().getCheckedItems().addListener((ListChangeListener<String>) c -> {
            ObservableList<String> selectedItems = deviceGroupCheckComboBox.getCheckModel().getCheckedItems();
            if (selectedItems.isEmpty()) {
                deviceGroupCheckComboBox.setTitle("请选择设备组");
            } else {
                // 将选中的组名拼接显示
                String combined = String.join(", ", selectedItems);
                // 详情页宽度有限（180.0），如果选多了建议简略显示
                if (selectedItems.size() > 1) {
                    deviceGroupCheckComboBox.setTitle("已选 " + selectedItems.size() + " 个组");
                } else {
                    deviceGroupCheckComboBox.setTitle(combined);
                }
            }
        });

        deviceGroupSelectorContainer.getChildren().clear();
        deviceGroupSelectorContainer.getChildren().add(deviceGroupCheckComboBox);
    }

    /**
     * 动态读取设备组数据
     */
    private void loadAttendanceGroups() {
        Task<List<AttendanceGroup>> task = new Task<List<AttendanceGroup>>() {
            @Override
            protected List<AttendanceGroup> call() throws Exception {
                return oaEmployeeService.getAttendanceGroups();
            }
        };

        task.setOnSucceeded(e -> {
            List<AttendanceGroup> results = task.getValue();
            if (results != null) {
                this.allAttendanceGroups = results;
                List<String> names = results.stream()
                        .map(AttendanceGroup::getGroupName)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                Platform.runLater(() -> deviceGroups.setAll(names));
            }
        });

        task.setOnFailed(e -> {
            Platform.runLater(() -> {
                if (lblMessage != null) lblMessage.setText("加载设备组失败");
                e.getSource().getException().printStackTrace();
            });
        });

        new Thread(task).start();
    }

    public void setEmployeeInfo(OaEmployee employee) {
        this.employee = employee;
        if (employee != null) {
            pinLabel.setText(employee.getPin() != null ? employee.getPin() : "");
            nameLabel.setText(employee.getName() != null ? employee.getName() : "");
            deptLabel.setText(employee.getDept() != null ? employee.getDept() : "");
            officeLocationLabel.setText(employee.getOfficeLocation() != null ? employee.getOfficeLocation() : "");
            entryDateLabel.setText(employee.getEntryDate() != null ?
                    AppConstants.dateTimeFormatter(employee.getEntryDate(), AppConstants.YYYY_MM_DD) :
                    AppConstants.dateTimeFormatter(LocalDateTime.now(), AppConstants.YYYY_MM_DD));
            statusLabel.setText(employee.getInService() != null && employee.getInService() ? "在职" : "离职");

            // 加载照片
            if (employee.getPhotoBase64() != null && !employee.getPhotoBase64().isEmpty()) {
                Image img = ImageConverter.base64ToImage(employee.getPhotoBase64());
                photoImageView.setImage(img != null ? img : DEFAULT_AVATAR);
                this.currentPhoto = img;
            }

            // 加载指纹
            if (employee.getFingerprint() != null && !employee.getFingerprint().isEmpty()) {
                Image img = ImageConverter.base64ToImage(employee.getFingerprint());
                if (img != null) {
                    fingerprint1ImageView.setImage(img);
                    this.currentFingerprint1Base64 = employee.getFingerprint();
                }
            }

        }
    }

    @FXML
    private void handleConfirm() {
        if (employee == null) return;

        // 1. 组装数据到 SyncRequest DTO
        SyncRequest syncRequest = new SyncRequest();
        syncRequest.setPin(employee.getPin());
        syncRequest.setName(employee.getName());

        // 处理当前最新的照片 (优先使用内存中新拍摄/上传的)
        if (currentPhoto != null) {
            syncRequest.setPhotoBase64(ImageConverter.javafxImageToBase64(currentPhoto));
        } else {
            syncRequest.setPhotoBase64(employee.getPhotoBase64());
        }

        // 处理当前最新的指纹
        if (currentFingerprint1Base64 != null) {
            syncRequest.setFingerprint(currentFingerprint1Base64);
        } else {
            syncRequest.setFingerprint(employee.getFingerprint());
        }

        // 2. 开启异步任务保存到数据库
        loadingManager.show("正在保存数据...");
        confirmButton.setDisable(true);

        Task<Void> saveTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                // 调用 Service 保存到数据库（employee_sync_queue 表）
                oaEmployeeService.saveSyncData(syncRequest);
                return null;
            }
        };

        saveTask.setOnSucceeded(e -> {
            loadingManager.hide();
            confirmButton.setDisable(false);
            Platform.runLater(() -> {
                // 更新内存中的 employee 对象，确保 UI 列表同步
                employee.setPhotoBase64(syncRequest.getPhotoBase64());
                employee.setFingerprint(syncRequest.getFingerprint());

                lblMessage.setText("保存并同步队列成功！");

                // 执行关闭逻辑
                if (onCloseRequest != null) {
                    onCloseRequest.run();
                }
                cleanup();
            });
        });

        saveTask.setOnFailed(e -> {
            loadingManager.hide();
            confirmButton.setDisable(false);
            Throwable ex = saveTask.getException();
            Platform.runLater(() -> {
                CustomAlertDialog.showError("保存失败", "无法写入数据库");
                lblMessage.setText("保存失败，请重试");
            });
        });

        new Thread(saveTask).start();
    }

    // --- 拍照、上传、指纹及其他逻辑 (保持不变) ---
    @FXML
    private void handleCapturePhoto() {
        loadingManager.show("正在打开相机.......");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/CameraPreviewView.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Parent root = loader.load();
            CameraPreviewController cameraController = loader.getController();

            Stage cameraStage = new Stage();
            cameraStage.initStyle(StageStyle.UNDECORATED);
            cameraStage.setScene(new Scene(root));
            cameraStage.showAndWait();

            if (cameraController.isConfirmed()) {
                String photoBase64 = cameraController.getPhotoBase64();
                if (photoBase64 != null) {
                    Image img = ImageConverter.base64ToImage(photoBase64);
                    photoImageView.setImage(img);
                    this.currentPhoto = img;
                    lblMessage.setText("照片录入成功");
                }
            }
        } catch (IOException e) {
            lblMessage.setText("摄像头启动失败: " + e.getMessage());
        }
    }

    @FXML
    private void handleUploadPhoto() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择员工照片");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("图片文件", "*.jpg", "*.png", "*.jpeg"));
        File file = fileChooser.showOpenDialog(uploadButton.getScene().getWindow());
        if (file != null) {
            try {
                Image image = new Image(new FileInputStream(file));
                photoImageView.setImage(image);
                this.currentPhoto = image;
                lblMessage.setText("照片上传成功");
            } catch (FileNotFoundException e) {
                lblMessage.setText("文件读取错误");
            }
        }
    }

    @FXML
    private void handleReconnectDevice() {
        lblMessage.setText("正在尝试重新连接指纹设备...");
        if (!isConnecting) tryConnectDevice(true);
    }

    @FXML
    private void handleFingerEnroll() {
        enrollButton.setDisable(true);
        verifyButton.setDisable(true);
        lblMessage.setText("请将手指放在指纹仪器上...");
        lblMessage.setStyle("-fx-text-fill: orange;");

        new Thread(() -> {
            FingerprintResult result = FingerprintUtil.captureAndExtract(500);
            Platform.runLater(() -> {
                if (result != null) {
                    this.capturedResult = result;
                    this.currentFingerprint1Base64 = result.getTemplateBase64();
                    fingerprint1ImageView.setImage(result.getFingerprintImage());
                    lblMessage.setText("指紋采集成功！");
                    lblMessage.setStyle("-fx-text-fill: green;");
                    verifyButton.setDisable(false);
                } else {
                    lblMessage.setText("采集失败，请重试。");
                    lblMessage.setStyle("-fx-text-fill: red;");
                }
                enrollButton.setDisable(false);
            });
        }).start();
    }

    @FXML
    private void handleFingerVerify() {
        if (capturedResult == null) {
            CustomAlertDialog.showWarning("比对失败", "请先采集指纹。");
            return;
        }
        String storedBase64 = employee.getFingerprint();
        if (storedBase64 == null || storedBase64.isEmpty()) {
            CustomAlertDialog.showWarning("比对失败", "数据库中无指纹记录。");
            return;
        }
        byte[] storedTemplate = FingerprintUtil.base64ToBlob(storedBase64);
        boolean matched = FingerprintUtil.verify(capturedResult.getTemplate(), storedTemplate, 0);
        if (matched) {
            lblMessage.setText("比对成功：指纹匹配。");
            lblMessage.setStyle("-fx-text-fill: green;");
        } else {
            lblMessage.setText("比对失败：指纹不匹配！");
            lblMessage.setStyle("-fx-text-fill: red;");
        }
    }

    /**
     * 同步按钮逻辑：必须勾选设备组
     */
    @FXML
    private void handleSynchronize() {
        // 1. 获取选中的组名
        ObservableList<String> selectedGroupNames = deviceGroupCheckComboBox.getCheckModel().getCheckedItems();

        if (selectedGroupNames.isEmpty()) {
            CustomAlertDialog.showWarning("操作提示", "请先勾选至少一个设备组进行同步。");
            return;
        }

        // 2. 提取选中组的所有设备 SN (去重合并)
        String combinedSns = allAttendanceGroups.stream()
                .filter(group -> selectedGroupNames.contains(group.getGroupName()))
                .map(AttendanceGroup::getDeviceSnsString)
                .filter(sns -> sns != null && !sns.isEmpty())
                .collect(Collectors.joining(","));

        if (combinedSns.isEmpty()) {
            CustomAlertDialog.showWarning("同步失败", "选中的设备组内没有绑定任何设备。");
            return;
        }

        // 3. 为当前这一个员工构建同步请求 (由于是详情页，通常只同步当前 employee)
        if (employee == null) return;

        SyncRequest request = new SyncRequest();
        request.setPin(employee.getPin());
        request.setName(employee.getName());
        // 使用当前最新的指纹和照片数据（如果有的话）
        request.setFingerprint(currentFingerprint1Base64 != null ? currentFingerprint1Base64 : employee.getFingerprint());
        request.setPhotoBase64(currentPhoto != null ? ImageConverter.javafxImageToBase64(currentPhoto) : employee.getPhotoBase64());
        request.setDeviceSn(combinedSns);

        request.setFingerSize(employee.getFingerSize());
        request.setPhotoSize(employee.getPhotoSize());

        List<SyncRequest> requests = Collections.singletonList(request);

        // 4. 执行同步
        performSynchronization(requests);
    }

    private void performSynchronization(List<SyncRequest> requests) {
        loadingManager.show("正在执行同步......");
        syncButton.setDisable(true); // 防止重复点击

        Task<Void> syncTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                // 调用服务层执行同步
                oaEmployeeService.syncEmployeesToGroup(requests);
                return null;
            }
        };

        syncTask.setOnSucceeded(e -> {
            loadingManager.hide();
            syncButton.setDisable(false);
            Platform.runLater(() -> {
                CustomAlertDialog.showInfo("同步成功", "已成功发送员工 [" + employee.getName() + "] 的同步请求。");
                // 同步成功后可以选择是否关闭抽屉
                if (onCloseRequest != null) {
                    onCloseRequest.run();
                }
            });
        });

        syncTask.setOnFailed(e -> {
            syncButton.setDisable(false);
            Throwable exception = syncTask.getException();
            if (exception instanceof SocketTimeoutException || (exception.getMessage() != null && exception.getMessage().contains("timeout"))) {
                loadingManager.showTimeout(() -> performSynchronization(requests));
            } else {
                loadingManager.hide();
                CustomAlertDialog.showError("同步失败", "错误");
            }
        });

        new Thread(syncTask).start();
    }

    private void tryConnectDevice(boolean isReconnect) {
        this.isConnecting = true;
        setUiConnectingState(isReconnect);

        Task<Boolean> connectTask = new Task<Boolean>() {
            @Override
            protected Boolean call() {
                try {
                    // 强制触发一次类加载，看 DLL 在不在
                    return FingerprintUtil.initAndConnect();
                } catch (Throwable t) {
                    // 如果是打包导致的 DLL 缺失，这里会抓住 Error
                    Platform.runLater(() -> {
                        CustomAlertDialog.showError("驱动异常", "无法加载指纹仪驱动模块");
                    });
                    return false;
                }
            }
        };

        connectTask.setOnSucceeded(e -> {
            this.isConnecting = false;
            updateConnectionStatus(connectTask.getValue());
        });

        connectTask.setOnFailed(e -> {
            this.isConnecting = false;
            Throwable ex = connectTask.getException();
            // 哪怕失败了也弹个窗，不要只写日志
            Platform.runLater(() -> {
                CustomAlertDialog.showError("连接崩溃", "指纹机连接线程发生错误");
            });
            updateConnectionStatus(false);
        });

        Thread t = new Thread(connectTask);
        t.setDaemon(true); // 守护线程，防止程序关闭不了
        t.start();
    }

    private void setUiConnectingState(boolean isReconnect) {
        enrollButton.setDisable(true);
        verifyButton.setDisable(true);
        reconnectButton.setDisable(true);
        if (isReconnect) reconnectButton.setVisible(true);
        lblMessage.setText(isReconnect ? "正常尝试重新连接设备 (5秒超時)..." : "正在尝试连接设备...");
        lblMessage.setStyle("-fx-text-fill: orange;");
    }

    private void updateConnectionStatus(boolean connected) {
        Platform.runLater(() -> {
            if (connected) {
                lblMessage.setText("指纹设备已连接。");
                lblMessage.setStyle("-fx-text-fill: green;");
                reconnectButton.setVisible(false);
                enrollButton.setDisable(false);
                // 【新增】开始心跳监测
                startHeartbeat();
            } else {
                lblMessage.setText("连接设备失败。请检查并重新连接。");
                lblMessage.setStyle("-fx-text-fill: red;");
                reconnectButton.setVisible(true);
                reconnectButton.setDisable(false);
                enrollButton.setDisable(true);
                // 【新增】停止监测
                stopHeartbeat();
            }
        });
    }

    private void startHeartbeat() {
        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) return;

        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            // 调用 Util 层检测设备是否还在
            // 注意：这个方法必须非常轻量，不能阻塞
            boolean stillConnected = FingerprintUtil.initAndConnect();

            if (!stillConnected) {
                Platform.runLater(() -> {
                    updateConnectionStatus(false);
                    lblMessage.setText("设备已断开连接！");
                    stopHeartbeat(); // 断开了就没必要再检测了，直到下次手动重连成功
                });
            }
        }, 30, 30, TimeUnit.SECONDS); // 每30秒检测一次
    }

    private void stopHeartbeat() {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor = null;
        }
    }

    private void cleanup() {
        stopHeartbeat();
        FingerprintUtil.destroy();
    }

    @FXML
    private void handleCancel() {
        if (onCloseRequest != null) onCloseRequest.run();
        cleanup();
    }

    @FXML
    private void handleImageClickToZoom(MouseEvent event) {
        if (this.currentPhoto != null) ShowZoomImageWindow.showZoomWindow(this.currentPhoto);
    }

    @FXML
    private void handleFingerprintClickToZoom(MouseEvent event) {
        if (fingerprint1ImageView.getImage() != DEFAULT_FINGERPRINT)
            ShowZoomImageWindow.showZoomWindow(fingerprint1ImageView.getImage());
    }
}