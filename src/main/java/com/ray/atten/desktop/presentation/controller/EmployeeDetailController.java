package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.dto.FingerprintResult;
import com.ray.atten.desktop.dto.SyncRequest;
import com.ray.atten.desktop.model.AttendanceGroup;
import com.ray.atten.desktop.model.EmployeeSync;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
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
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class EmployeeDetailController {

    @FXML
    private ImageView photoImageView;
    @FXML
    private ImageView fingerprint1ImageView;

    @FXML
    private Label pinLabel, nameLabel, deptLabel, officeLocationLabel, entryDateLabel, statusLabel, lblMessage;
    @FXML
    private Button captureButton, uploadButton, enrollButton, reconnectButton, verifyButton, confirmButton, cancelButton, syncButton;

    @FXML
    private ComboBox<OaEmployee.FingerStatus> fingerStatusComboBox;

    @FXML
    private StackPane deviceGroupSelectorContainer;
    private CheckComboBox<String> deviceGroupCheckComboBox;
    private final ObservableList<String> deviceGroups = FXCollections.observableArrayList();

    private List<AttendanceGroup> allAttendanceGroups = new ArrayList<>();

    @Autowired
    private OaEmployeeService oaEmployeeService;

    private OaEmployee employee;
    private volatile boolean isConnecting = false;
    private FingerprintResult capturedResult;

    private ScheduledExecutorService heartbeatExecutor;

    @Autowired
    private ApplicationContext springContext;
    @Autowired
    private LoadingManager loadingManager;

    // 临时存储当前修改的指纹 Base64 (Key 为 FID)
    private Map<Integer, String> tempFingerprintMap = new HashMap<>();
    // 临时存储当前修改的照片 Base64
    private String tempPhotoBase64 = null;

    private final Image DEFAULT_AVATAR = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/default_avatar.png")));
    private final Image DEFAULT_FINGERPRINT = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/default_fingerprint.png")));
    private final Image DEFAULT_EXISTS_FINGERPRINT = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/fingerprint_exists.png")));
    private Runnable onCloseRequest;

    public void setOnCloseRequest(Runnable onCloseRequest) {
        this.onCloseRequest = onCloseRequest;
    }

    @FXML
    public void initialize() {
        if (photoImageView != null) photoImageView.setImage(DEFAULT_AVATAR);
        if (fingerprint1ImageView != null) fingerprint1ImageView.setImage(DEFAULT_FINGERPRINT);

        if (reconnectButton != null) reconnectButton.setVisible(false);
        if (lblMessage != null) lblMessage.setText("正在加载配置...");

        initFingerSelector();
        initDeviceGroupSelector();

        Platform.runLater(() -> {
            loadAttendanceGroups();
            tryConnectDevice(false);
        });
    }

    private void initFingerSelector() {
        // 监听下拉框，切换手指时更新图片显示
        fingerStatusComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updateFingerprintImageView(newVal.getFid());
            }
        });
    }

    private void initDeviceGroupSelector() {
        if (deviceGroupSelectorContainer == null) return;
        deviceGroupCheckComboBox = new CheckComboBox<>(deviceGroups);
        deviceGroupCheckComboBox.setPrefWidth(180.0);
        deviceGroupCheckComboBox.setMaxWidth(Double.MAX_VALUE);
        deviceGroupCheckComboBox.setTitle("请选择设备组");

        deviceGroupCheckComboBox.getCheckModel().getCheckedItems().addListener((ListChangeListener<String>) c -> {
            ObservableList<String> selectedItems = deviceGroupCheckComboBox.getCheckModel().getCheckedItems();
            if (selectedItems.isEmpty()) {
                deviceGroupCheckComboBox.setTitle("请选择设备组");
            } else {
                String combined = String.join(", ", selectedItems);
                deviceGroupCheckComboBox.setTitle(selectedItems.size() > 1 ? "已选 " + selectedItems.size() + " 个组" : combined);
            }
        });

        deviceGroupSelectorContainer.getChildren().clear();
        deviceGroupSelectorContainer.getChildren().add(deviceGroupCheckComboBox);
    }

    private void loadAttendanceGroups() {
        Task<List<AttendanceGroup>> task = new Task<>() {
            @Override
            protected List<AttendanceGroup> call() throws IOException {
                return oaEmployeeService.getAttendanceGroups();
            }
        };
        task.setOnSucceeded(e -> {
            this.allAttendanceGroups = task.getValue();
            if (allAttendanceGroups != null) {
                List<String> names = allAttendanceGroups.stream().map(AttendanceGroup::getGroupName).collect(Collectors.toList());
                deviceGroups.setAll(names);
            }
        });
        new Thread(task).start();
    }

    public void setEmployeeInfo(OaEmployee employee) {
        // 【核心修复】切换员工时，必须清空上一位员工的残留数据
        this.tempFingerprintMap.clear();
        this.tempPhotoBase64 = null;
        this.capturedResult = null;

        // 清除 UI 状态
        if (photoImageView != null) photoImageView.setImage(DEFAULT_AVATAR);
        if (fingerprint1ImageView != null) fingerprint1ImageView.setImage(DEFAULT_FINGERPRINT);
        if (lblMessage != null) {
            lblMessage.setText("");
            lblMessage.setStyle("");
        }

        this.employee = employee;
        if (employee != null) {
            pinLabel.setText(employee.getPin());
            nameLabel.setText(employee.getName());
            deptLabel.setText(employee.getDept());
            officeLocationLabel.setText(employee.getOfficeLocation());
            entryDateLabel.setText(employee.getEntryDate() != null ?
                    AppConstants.dateTimeFormatter(employee.getEntryDate(), AppConstants.YYYY_MM_DD) : "");
            statusLabel.setText(employee.getInService() != null && employee.getInService() ? "在职" : "离职");

            // 加载照片 (注意：这里使用 "photo" 字符串与后端对应)
            String photoBase64 = employee.getPhotoBase64();
            if (photoBase64 != null && !photoBase64.isEmpty()) {
                photoImageView.setImage(ImageConverter.base64ToImage(photoBase64));
            }

            refreshFingerStatusCombo();
            // 默认选中第一根手指并触发图片显示
            fingerStatusComboBox.getSelectionModel().selectFirst();
            if (fingerStatusComboBox.getSelectionModel().getSelectedItem() != null) {
                updateFingerprintImageView(fingerStatusComboBox.getSelectionModel().getSelectedItem().getFid());
            }
        }
    }

    private void updateFingerprintImageView(int fid) {
        // 1. 检查实时缓存（如果是刚录入且未关闭窗口，这里会有 capturedResult）
        if (capturedResult != null && tempFingerprintMap.containsKey(fid)) {
            if (capturedResult.getFingerprintImage() != null) {
                fingerprint1ImageView.setImage(capturedResult.getFingerprintImage());
                return;
            }
        }

        // 2. 检查是否有数据（内存暂存 或 数据库加载）
        String base64 = tempFingerprintMap.get(fid);
        if (base64 == null && employee != null) {
            base64 = employee.getFingerprintBase64(fid);
        }

        // 3. 渲染逻辑
        if (base64 != null && !base64.isEmpty()) {
            // 重要：不要尝试转换 base64 为图片，显示一个“指纹已存在”的图标
            // 请确保项目中 /images/ 目录下有 fingerprint_exists.png
            fingerprint1ImageView.setImage(DEFAULT_EXISTS_FINGERPRINT);
        } else {
            fingerprint1ImageView.setImage(DEFAULT_FINGERPRINT);
        }
    }

    private void refreshFingerStatusCombo() {
        if (employee == null) return;
        fingerStatusComboBox.setItems(FXCollections.observableArrayList(employee.getFingerStatuses()));
    }

    @FXML
    private void handleConfirm() {
        if (employee == null) return;

        SyncRequest syncRequest = new SyncRequest();
        syncRequest.setPin(employee.getPin());
        syncRequest.setName(employee.getName());

        // 确保存储到 syncList
        if (tempPhotoBase64 != null) {
            updateEmployeeSyncData("photo", null, tempPhotoBase64);
        }
        tempFingerprintMap.forEach((fid, base64) -> updateEmployeeSyncData("finger", fid, base64));

        // 提交完整的 syncList
        syncRequest.setFingerFidList(employee.getSyncList());

        loadingManager.show("正在保存数据...");
        confirmButton.setDisable(true);

        Task<Void> saveTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                oaEmployeeService.saveSyncData(syncRequest);
                return null;
            }
        };

        saveTask.setOnSucceeded(e -> {
            loadingManager.hide();
            confirmButton.setDisable(false);
            Platform.runLater(() -> {
                lblMessage.setText("保存数据成功！");
                if (onCloseRequest != null) onCloseRequest.run();
                cleanup();
            });
        });

        saveTask.setOnFailed(e -> {
            loadingManager.hide();
            confirmButton.setDisable(false);
            CustomAlertDialog.showError("保存失败", "无法写入数据库");
        });

        new Thread(saveTask).start();
    }

    @FXML
    private void handleCapturePhoto() {
        loadingManager.show("正在打开相机...");
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
                this.tempPhotoBase64 = cameraController.getPhotoBase64();
                photoImageView.setImage(ImageConverter.base64ToImage(tempPhotoBase64));
                lblMessage.setText("照片录入成功");
            }
        } catch (IOException e) {
            lblMessage.setText("摄像头启动失败");
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
                this.tempPhotoBase64 = ImageConverter.javafxImageToBase64(image);
                lblMessage.setText("照片上传成功");
            } catch (FileNotFoundException e) {
                lblMessage.setText("文件读取错误");
            }
        }
    }

    @FXML
    private void handleFingerEnroll() {
        OaEmployee.FingerStatus selectedStatus = fingerStatusComboBox.getSelectionModel().getSelectedItem();
        if (selectedStatus == null) {
            CustomAlertDialog.showWarning("提示", "请先选择要录入的手指。");
            return;
        }

        enrollButton.setDisable(true);
        lblMessage.setText("正在录入指纹 ");

        new Thread(() -> {
            // 1. 调用工具类采集：此时 FingerprintResult 内部会填充 Image 对象和模板字节数组
            FingerprintResult result = FingerprintUtil.captureAndExtract(500);

            Platform.runLater(() -> {
                if (result != null && result.getTemplateBase64() != null) {
                    capturedResult = result;
                    int fid = selectedStatus.getFid();

                    // 2. 存储模板 Base64（用于比对和保存到数据库）
                    tempFingerprintMap.put(fid, result.getTemplateBase64());

                    // 3. 【核心修复】更新 UI 图片展示
                    if (result.getFingerprintImage() != null) {
                        // 直接使用对象展示，不要走 ImageConverter 转换特征码
                        fingerprint1ImageView.setImage(result.getFingerprintImage());
                    } else {
                        // 如果 SDK 没返回图像，显示一个“录入成功”的占位图标
                        // 绝不能在这里传 result.getTemplateBase64()
                        fingerprint1ImageView.setImage(DEFAULT_FINGERPRINT);
                        System.err.println("警告：指纹仪未返回图像对象，仅获取到特征模板。");
                    }

                    // 4. 更新内存中的 syncList 供后续保存使用
                    updateEmployeeSyncData("finger", fid, result.getTemplateBase64());

                    // 5. 刷新界面元素
                    refreshFingerStatusCombo();

                    // 重新选中当前手指
                    for (OaEmployee.FingerStatus fs : fingerStatusComboBox.getItems()) {
                        if (fs.getFid() == fid) {
                            fingerStatusComboBox.getSelectionModel().select(fs);
                            break;
                        }
                    }

                    lblMessage.setText("指纹 " + (fid + 1) + " 采集成功！");
                    lblMessage.setStyle("-fx-text-fill: green;");
                    verifyButton.setDisable(false);
                } else {
                    lblMessage.setText("采集失败：请确保手指按压在传感器中心。");
                    lblMessage.setStyle("-fx-text-fill: red;");
                }
                enrollButton.setDisable(false);
            });
        }).start();
    }

    private void updateEmployeeSyncData(String type, Integer fid, String base64) {
        if (employee == null || employee.getSyncList() == null) return;

        // 查找是否存在相同类型且相同 FID 的记录
        Optional<EmployeeSync> existing = employee.getSyncList().stream()
                .filter(s -> type.equals(s.getType()) && Objects.equals(fid, s.getFid()))
                .findFirst();

        if (existing.isPresent()) {
            existing.get().setBase64Data(base64);
        } else {
            EmployeeSync newDto = new EmployeeSync();
            newDto.setFid(fid);
            newDto.setBase64Data(base64);
            newDto.setType(type);
            employee.getSyncList().add(newDto);
        }
    }

    @FXML
    private void handleFingerVerify() {
        OaEmployee.FingerStatus selectedStatus = fingerStatusComboBox.getSelectionModel().getSelectedItem();
        if (capturedResult == null || selectedStatus == null) {
            CustomAlertDialog.showWarning("比对失败", "请先采集指纹。");
            return;
        }

        String storedBase64 = tempFingerprintMap.getOrDefault(selectedStatus.getFid(),
                employee.getFingerprintBase64(selectedStatus.getFid()));

        if (storedBase64 == null || storedBase64.isEmpty()) {
            CustomAlertDialog.showWarning("比对失败", "该手指无录入记录。");
            return;
        }

        byte[] storedTemplate = FingerprintUtil.base64ToBlob(storedBase64);
        boolean matched = FingerprintUtil.verify(capturedResult.getTemplate(), storedTemplate, 0);
        lblMessage.setText(matched ? "比对成功：指纹匹配。" : "比对失败：指纹不匹配！");
        lblMessage.setStyle(matched ? "-fx-text-fill: green;" : "-fx-text-fill: red;");
    }

    @FXML
    private void handleSynchronize() {
        ObservableList<String> selectedGroups = deviceGroupCheckComboBox.getCheckModel().getCheckedItems();
        if (selectedGroups.isEmpty()) {
            CustomAlertDialog.showWarning("提示", "请选择设备组。");
            return;
        }

        String combinedSns = allAttendanceGroups.stream()
                .filter(g -> selectedGroups.contains(g.getGroupName()))
                .map(AttendanceGroup::getDeviceSnsString)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(","));

        if (combinedSns.isEmpty()) return;

        SyncRequest request = new SyncRequest();
        request.setPin(employee.getPin());
        request.setName(employee.getName());
        request.setDeviceSn(combinedSns);

        // 实时更新当前所有指纹和照片进入同步包
        if (tempPhotoBase64 != null) updateEmployeeSyncData("photo", null, tempPhotoBase64);
        tempFingerprintMap.forEach((fid, base64) -> updateEmployeeSyncData("finger", fid, base64));
        request.setFingerFidList(employee.getSyncList());

        performSynchronization(Collections.singletonList(request));
    }

    private void performSynchronization(List<SyncRequest> requests) {
        loadingManager.show("正在同步...");
        syncButton.setDisable(true);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                oaEmployeeService.syncEmployeesToGroup(requests);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            loadingManager.hide();
            syncButton.setDisable(false);
            CustomAlertDialog.showInfo("成功", "同步请求已发送。");

            // --- 核心修复：同步成功后也触发刷新 ---
            Platform.runLater(() -> {
                if (onCloseRequest != null) {
                    onCloseRequest.run();
                }
            });
        });

        task.setOnFailed(e -> {
            loadingManager.hide();
            syncButton.setDisable(false);
            CustomAlertDialog.showError("失败", "同步发生错误");
        });
        new Thread(task).start();
    }

    // --- 设备连接与心跳逻辑 (保持原样) ---
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

        // 显式清空，防止单例 Bean 导致的内存泄漏和数据污染
        tempFingerprintMap.clear();
        tempPhotoBase64 = null;
        capturedResult = null;
        employee = null;
    }

    @FXML
    private void handleCancel() {
        if (onCloseRequest != null) onCloseRequest.run();
        cleanup();
    }

    @FXML
    private void handleImageClickToZoom() {
        if (tempPhotoBase64 != null) {
            ShowZoomImageWindow.showZoomWindow(ImageConverter.base64ToImage(tempPhotoBase64));
        } else if (employee.getPhotoBase64() != null) {
            ShowZoomImageWindow.showZoomWindow(ImageConverter.base64ToImage(employee.getPhotoBase64()));
        }
    }

    @FXML
    private void handleFingerprintClickToZoom() {
        Image img = fingerprint1ImageView.getImage();
        if (img != DEFAULT_FINGERPRINT) ShowZoomImageWindow.showZoomWindow(img);
    }

    @FXML
    private void handleReconnectDevice() {
        lblMessage.setText("正在尝试重新连接指纹设备...");
        if (!isConnecting) tryConnectDevice(true);
    }

}
