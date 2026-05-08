package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.dto.FingerprintResult;
import com.ray.atten.desktop.model.OaEmployee;
import com.ray.atten.desktop.utils.CustomAlertDialog;
import com.ray.atten.desktop.utils.FingerprintUtil;
import com.ray.atten.desktop.utils.ImageConverter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Scope;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * 指纹采集与比对窗口控制器。
 */
@Component
@Scope("prototype")
public class FingerprintCaptureController {

    @FXML
    private Label pinLabel;
    @FXML
    private Label nameLabel;
    @FXML
    private Label statusMessageLabel;
    @FXML
    private ImageView fingerprintDisplay;
    @FXML
    private Button reconnectButton;
    @FXML
    private Button captureButton;
    @FXML
    private Button verifyButton;
    @FXML
    private Button saveButton;
    @FXML
    private Button cancelButton;

    // 指纹选择下拉框
    @FXML
    private ComboBox<OaEmployee.FingerStatus> fingerStatusComboBox;

    private OaEmployee employee;

    private FingerprintResult capturedResult;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private volatile boolean isConnecting = false;

    private final Image DEFAULT_FINGERPRINT_IMAGE = new Image(
            Objects.requireNonNull(getClass().getResourceAsStream("/images/default_fingerprint.png")));

    @FXML
    public void initialize() {
        if (fingerprintDisplay != null) {
            fingerprintDisplay.setImage(DEFAULT_FINGERPRINT_IMAGE);
        }
        reconnectButton.setVisible(false);

        // 监听下拉框切换
        initFingerSelector();

        tryConnectDevice(false);
    }

    private void initFingerSelector() {
        if (fingerStatusComboBox == null) return;
        fingerStatusComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // 切换手指时，清除当前采集未保存的结果，并回显该手指已有的指纹
                capturedResult = null;
                verifyButton.setDisable(true);
                saveButton.setDisable(true);
                updateFingerprintDisplay(newVal.getFid());
            }
        });
    }

    private void updateFingerprintDisplay(int fid) {
        if (employee == null) return;
        String base64 = employee.getFingerprintBase64(fid);
        if (base64 != null && !base64.isEmpty()) {
            fingerprintDisplay.setImage(ImageConverter.base64ToImage(base64));
        } else {
            fingerprintDisplay.setImage(DEFAULT_FINGERPRINT_IMAGE);
        }
    }

    public void setEmployeeInfo(OaEmployee employee) {
        this.employee = employee;
        if (employee != null) {
            pinLabel.setText(employee.getPin() != null ? employee.getPin() : "[N/A]");
            nameLabel.setText(employee.getName() != null ? employee.getName() : "[未设置]");

            // 初始化下拉框数据
            if (fingerStatusComboBox != null) {
                fingerStatusComboBox.setItems(FXCollections.observableArrayList(employee.getFingerStatuses()));
                fingerStatusComboBox.getSelectionModel().selectFirst();
            }
        }
    }

    /**
     * 采集指纹。
     */
    @FXML
    private void handleCaptureFingerprint() {
        OaEmployee.FingerStatus selected = fingerStatusComboBox.getSelectionModel().getSelectedItem();
        if (selected == null) {
            CustomAlertDialog.showWarning("提示", "请先选择手指位置。");
            return;
        }

        captureButton.setDisable(true);
        verifyButton.setDisable(true);
        saveButton.setDisable(true);
        statusMessageLabel.setText("请将 [" + selected.toString() + "] 放在指纹仪器上...");
        statusMessageLabel.setStyle("-fx-text-fill: orange;");

        new Thread(() -> {
            FingerprintResult result = FingerprintUtil.captureAndExtract(500);

            Platform.runLater(() -> {
                if (result != null) {
                    this.capturedResult = result;
                    fingerprintDisplay.setImage(result.getFingerprintImage());
                    statusMessageLabel.setText("指纹采集成功！");
                    statusMessageLabel.setStyle("-fx-text-fill: green;");

                    verifyButton.setDisable(false);
                    saveButton.setDisable(false);
                } else {
                    statusMessageLabel.setText("采集失败，请重试。");
                    statusMessageLabel.setStyle("-fx-text-fill: red;");
                }
                captureButton.setDisable(false);
            });
        }).start();
    }

    /**
     * 比对指纹。
     */
    @FXML
    private void handleVerifyFingerprint() {
        if (capturedResult == null) return;

        OaEmployee.FingerStatus selected = fingerStatusComboBox.getSelectionModel().getSelectedItem();
        String storedBase64 = employee.getFingerprintBase64(selected.getFid());

        if (storedBase64 == null || storedBase64.isEmpty()) {
            CustomAlertDialog.showWarning("比对失败", "数据库中没有该手指的记录。");
            return;
        }

        byte[] storedTemplate = FingerprintUtil.base64ToBlob(storedBase64);
        boolean matched = FingerprintUtil.verify(capturedResult.getTemplate(), storedTemplate, 0);

        if (matched) {
            CustomAlertDialog.showInfo("结果", "匹配成功！");
        } else {
            CustomAlertDialog.showWarning("结果", "指纹不匹配！");
        }
    }

    /**
     * 保存指纹。
     * 由于 FingerprintResult 没有 fid 字段，我们使用 Map 将 FID 和 Result 一起返回给调用者。
     */
    @FXML
    private void handleSaveFingerprint() {
        if (capturedResult == null) return;

        OaEmployee.FingerStatus selected = fingerStatusComboBox.getSelectionModel().getSelectedItem();

        // 封装返回数据
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("fid", selected.getFid());
        resultData.put("result", capturedResult);

        Stage stage = (Stage) saveButton.getScene().getWindow();
        stage.setUserData(resultData); // 调用者通过 stage.getUserData() 获取这个 Map

        // 注意：这里不要 destroy，由外部或 shutdown 方法统一处理
        stage.close();
    }

    // --- 连接管理逻辑 ---

    private void tryConnectDevice(boolean isReconnect) {
        this.isConnecting = true;
        setUiConnectingState(isReconnect);
        executor.submit(() -> {
            boolean success = FingerprintUtil.initAndConnect();
            Platform.runLater(() -> {
                this.isConnecting = false;
                updateConnectionStatus(success);
            });
        });
    }

    private void setUiConnectingState(boolean isReconnect) {
        captureButton.setDisable(true);
        reconnectButton.setDisable(true);
        statusMessageLabel.setText("正在连接指纹仪...");
        statusMessageLabel.setStyle("-fx-text-fill: orange;");
    }

    private void updateConnectionStatus(boolean connected) {
        if (connected) {
            statusMessageLabel.setText("设备已连接。");
            statusMessageLabel.setStyle("-fx-text-fill: green;");
            reconnectButton.setVisible(false);
            captureButton.setDisable(false);
        } else {
            statusMessageLabel.setText("连接失败，请检查硬件。");
            statusMessageLabel.setStyle("-fx-text-fill: red;");
            reconnectButton.setVisible(true);
            reconnectButton.setDisable(false);
        }
    }

    @FXML
    private void handleReconnect() {
        if (!isConnecting) tryConnectDevice(true);
    }

    @FXML
    private void handleCancel() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

    public void shutdown() {
        FingerprintUtil.destroy();
        executor.shutdownNow();
    }
}