package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.dto.FingerprintResult;
import com.ray.atten.desktop.model.OaEmployee;
import com.ray.atten.desktop.utils.CustomAlertDialog;
import com.ray.atten.desktop.utils.FingerprintUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Scope;

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

    private OaEmployee employee;

    private FingerprintResult capturedResult;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // 【新增】连接状态标志，防止用户在连接过程中重复点击
    private volatile boolean isConnecting = false;

    private final Image DEFAULT_FINGERPRINT_IMAGE = new Image(
            getClass().getResourceAsStream("/images/default_fingerprint.png"));

    @FXML
    public void initialize() {
        if (DEFAULT_FINGERPRINT_IMAGE.getException() == null) {
            fingerprintDisplay.setImage(DEFAULT_FINGERPRINT_IMAGE);
        }
        // 初始时不显示按钮，等待第一次连接结果决定是否显示
        reconnectButton.setVisible(false);
        tryConnectDevice(false);
    }

    /**
     * 设置员工信息（由主窗口调用）。
     */
    public void setEmployeeInfo(OaEmployee employee) {
        this.employee = employee;
        if (employee != null) {
            pinLabel.setText(employee.getPin() != null ? employee.getPin() : "[N/A]");
            nameLabel.setText(employee.getName() != null ? employee.getName() : "[未设置]");
        }
    }

    /**
     * 【修正】设置连接过程中的 UI 状态，确保即时反馈：重连按钮禁用，提示“重连中”。
     *
     * @param isReconnect 是否是用户手动点击重连按钮触发
     */
    private void setUiConnectingState(boolean isReconnect) {
        // 禁用所有操作按钮
        captureButton.setDisable(true);
        verifyButton.setDisable(true);
        saveButton.setDisable(true);

        // 【关键】无论是否手动重连，立即禁用重连按钮，防止二次点击
        reconnectButton.setDisable(true);

        // 如果是手动重连，确保按钮可见，提示用户重连正在进行
        if (isReconnect) {
            reconnectButton.setVisible(true);
        }

        // 立即更新提示信息
        statusMessageLabel.setText(isReconnect ? "正在尝试重新连接设备 (10秒超时)..." : "正在尝试重新连接设备 (10秒超时)...");
        statusMessageLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: orange;");
    }

    /**
     * 【修正】尝试连接指纹仪设备，并更新 UI 状态。
     *
     * @param isReconnect 是否是用户手动点击重连按钮触发
     */
    private void tryConnectDevice(boolean isReconnect) {

        // 【修正】在执行UI状态设置前，先设置全局连接状态标志（确保即使跳过 handleReconnect 也不会重复连接）
        this.isConnecting = true;

        // 1. 设置 UI 为尝试连接的过渡状态 (在 FX Thread 中同步执行，立即反馈)
        setUiConnectingState(isReconnect);

        // 2. 提交连接任务到线程池
        Callable<Boolean> connectionTask = FingerprintUtil::initAndConnect;
        Future<Boolean> future = executor.submit(connectionTask);

        // 3. 启动新线程来等待结果并处理超时
        new Thread(() -> {
            boolean success = false;
            // String errorMessage = null; // 移除错误信息变量，因为要求不提示异常内容

            try {
                // 等待连接结果，最长等待 10 秒 (阻塞点)
                success = future.get(10, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                // 超时：视为失败
                future.cancel(true);
                FingerprintUtil.destroy();
            } catch (InterruptedException | ExecutionException e) {
                // 其他异常：视为失败
            } catch (Exception e) {
                // 捕获所有运行时异常，如 JNI 错误
                System.err.println("Unexpected error during connection: " + e.getMessage());
            } finally {
                // 【核心修正】无论如何，确保连接状态被重置，并更新 UI
                final boolean finalSuccess = success;

                Platform.runLater(() -> {
                    // 【关键】连接尝试结束，重置全局状态标志
                    this.isConnecting = false;
                    updateConnectionStatus(finalSuccess);
                });
            }

            // 4. 在 JavaFX 线程中更新最终状态
            final boolean finalSuccess = success;

            Platform.runLater(() -> {
                updateConnectionStatus(finalSuccess);
            });
        }).start();
    }

    /**
     * 【修正】根据连接结果更新 UI 状态，严格遵循设计要求。
     *
     * @param connected 是否成功连接
     */
    private void updateConnectionStatus(boolean connected) {
        if (connected) {
            // ========================= 连接成功 =========================
            statusMessageLabel.setText("设备已连接，可以采集指纹。");
            statusMessageLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: green;");

            // 满足要求：连接成功，隐藏重连按钮，启用采集
            reconnectButton.setVisible(false); // 隐藏按钮
            reconnectButton.setDisable(true); // 隐藏了，禁用状态保持一致
            captureButton.setDisable(false); // 启用采集

            verifyButton.setDisable(true);
            saveButton.setDisable(true);
        } else {
            // ========================= 连接失败或超时 =========================

            // 满足要求：只提示通用失败信息，不带异常细节
            statusMessageLabel.setText("连接设备失败。请检查设备，然后尝试重新连接。");
            statusMessageLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: red;");

            // 满足要求：连接失败，显示重连按钮，并确保它是可点击的
            reconnectButton.setVisible(true);
            reconnectButton.setDisable(false); // 【关键】重置按钮为可点击

            captureButton.setDisable(true);
            verifyButton.setDisable(true);
            saveButton.setDisable(true);
        }
    }

    /**
     * 尝试重新连接设备。
     */
    @FXML
    private void handleReconnect() {
        if (isConnecting) {
            // 已经在连接中，忽略本次点击，防止程序崩溃
            System.err.println("Warning: Reconnect already in progress. Ignoring rapid click.");
            return;
        }
        // 允许连接
        tryConnectDevice(true);
    }

    /**
     * 采集指纹。
     */
    @FXML
    private void handleCaptureFingerprint() {
        captureButton.setDisable(true);
        verifyButton.setDisable(true);
        saveButton.setDisable(true);
        statusMessageLabel.setText("请将手指放到指纹仪器上...");
        statusMessageLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: orange;");

        new Thread(() -> {
            FingerprintResult result = FingerprintUtil.captureAndExtract(500);

            Platform.runLater(() -> {
                if (result != null) {
                    capturedResult = result;
                    fingerprintDisplay.setImage(result.getFingerprintImage());
                    statusMessageLabel.setText("指纹采集成功！");
                    statusMessageLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: green;");

                    verifyButton.setDisable(false);
                    saveButton.setDisable(false);
                } else {
                    statusMessageLabel.setText("指纹采集失败或已取消。");
                    statusMessageLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: red;");
                    verifyButton.setDisable(true);
                    saveButton.setDisable(true);
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
        if (capturedResult == null) {
            CustomAlertDialog.showWarning("比对失败", "请先成功采集指纹。");
            return;
        }

        String storedBase64 = employee.getFingerprint();

        if (storedBase64 == null || storedBase64.isEmpty()) {
            CustomAlertDialog.showWarning("比对失败", "该员工未录入初始指纹，无法比对。");
            return;
        }

        byte[] storedTemplate = FingerprintUtil.base64ToBlob(storedBase64);
        if (storedTemplate == null || storedTemplate.length == 0) {
            CustomAlertDialog.showWarning("比对失败", "员工存储的指纹无效或转换失败。");
            return;
        }

        boolean matched = FingerprintUtil.verify(capturedResult.getTemplate(), storedTemplate, 0);

        if (matched) {
            CustomAlertDialog.showInfo("比对结果", "比对成功！指纹匹配。");
        } else {
            CustomAlertDialog.showWarning("比对结果", "比对失败！指纹不匹配。");
        }
    }

    /**
     * 保存指纹：将采集到的模板返回给主 Controller 处理。
     */
    @FXML
    private void handleSaveFingerprint() {
        if (capturedResult == null) {
            CustomAlertDialog.showWarning("白村失败", "没有可以保存的指纹数据。");
            return;
        }

        Stage stage = (Stage) saveButton.getScene().getWindow();
        stage.setUserData(capturedResult);
        FingerprintUtil.destroy();
        stage.close();
    }

    /**
     * 取消操作，关闭窗口。
     */
    @FXML
    private void handleCancel() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        FingerprintUtil.destroy();
        stage.close();
    }

    /**
     * 确保在窗口被外部关闭时释放资源。
     */
    public void shutdown() {
        FingerprintUtil.destroy();
        executor.shutdownNow();
    }
}