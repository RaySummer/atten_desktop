package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.dto.FingerprintResult;
import com.ray.atten.desktop.model.OaEmployee;
import com.ray.atten.desktop.presentation.controller.component.ShowZoomImageWindow;
import com.ray.atten.desktop.utils.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Component
public class EmployeeDetailController {

    @FXML
    private ImageView photoImageView;
    @FXML
    private ImageView fingerprint1ImageView;

    @FXML
    private Label pinLabel, nameLabel, deptLabel, officeLocationLabel, entryDateLabel, statusLabel, lblMessage;
    @FXML
    private Button captureButton, uploadButton, enrollButton, reconnectButton, verifyButton, confirmButton, cancelButton;

    private Image currentPhoto;
    private String currentFingerprint1Base64;
    private OaEmployee employee;
    private volatile boolean isConnecting = false;
    private FingerprintResult capturedResult; // 存储当前采集到的指纹对象

    @Autowired
    private ApplicationContext springContext;

    @Autowired
    private LoadingManager loadingManager;

    private double xOffset = 0;
    private double yOffset = 0;

    private final Image DEFAULT_AVATAR = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/default_avatar.png")));
    private final Image DEFAULT_FINGERPRINT = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/default_fingerprint.png")));
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Runnable onCloseRequest;

    public void setOnCloseRequest(Runnable onCloseRequest) {
        this.onCloseRequest = onCloseRequest;
    }

    @FXML
    public void initialize() {
        currentPhoto = null;
        photoImageView.setImage(DEFAULT_AVATAR);
        fingerprint1ImageView.setImage(DEFAULT_FINGERPRINT);
        // 初始 UI 状态
        reconnectButton.setVisible(false);
        lblMessage.setText("正在初始化指纹设备...");
        tryConnectDevice(false);
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

    // --- 拍照逻辑 (保持原有逻辑) ---
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
            cameraStage.initModality(Modality.NONE); // 建议拍照时模态，防止误点背景

            cameraStage.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                if (!isNowFocused) Platform.runLater(cameraController::shutdown);
            });

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

    // --- 指纹处理逻辑 ---

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

    private void tryConnectDevice(boolean isReconnect) {
        this.isConnecting = true;
        setUiConnectingState(isReconnect);

        executor.submit(() -> {
            boolean success = false;
            try {
                // 执行 SDK 连接，限时 10 秒
                Future<Boolean> future = executor.submit(FingerprintUtil::initAndConnect);
                success = future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                FingerprintUtil.destroy();
            } finally {
                final boolean finalSuccess = success;
                Platform.runLater(() -> {
                    this.isConnecting = false;
                    updateConnectionStatus(finalSuccess);
                });
            }
        });
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
        if (connected) {
            lblMessage.setText("指纹设备已连接。");
            lblMessage.setStyle("-fx-text-fill: green;");
            reconnectButton.setVisible(false);
            enrollButton.setDisable(false);
        } else {
            lblMessage.setText("连接设备失败。请检查并重新连接。");
            lblMessage.setStyle("-fx-text-fill: red;");
            reconnectButton.setVisible(true);
            reconnectButton.setDisable(false);
            enrollButton.setDisable(true);
        }
    }

    // --- 全局操作 ---

    @FXML
    private void handleConfirm() {
        if (employee != null) {
            if (currentPhoto != null) {
                employee.setPhotoBase64(ImageConverter.javafxImageToBase64(currentPhoto));
            }
            if (currentFingerprint1Base64 != null) {
                employee.setFingerprint(currentFingerprint1Base64);
            }
            lblMessage.setText("保存成功！");
        }

        if (onCloseRequest != null) {
            onCloseRequest.run();
        }
    }

    @FXML
    private void handleCancel() {
        if (onCloseRequest != null) {
            onCloseRequest.run();
        }
    }

    @FXML
    private void handleImageClickToZoom(MouseEvent event) {
        if (this.currentPhoto != null) ShowZoomImageWindow.showZoomWindow(this.currentPhoto);
    }

    @FXML
    private void handleFingerprintClickToZoom(MouseEvent event) {
        if (fingerprint1ImageView.getImage() != DEFAULT_FINGERPRINT) {
            ShowZoomImageWindow.showZoomWindow(fingerprint1ImageView.getImage());
        }
    }

}