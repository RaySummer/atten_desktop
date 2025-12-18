package com.ray.atten.desktop.presentation.controller;

import com.github.sarxos.webcam.Webcam;
import com.ray.atten.desktop.utils.ImageConverter;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.image.BufferedImage;

@Component
public class CameraPreviewController {

    @FXML
    private ImageView videoImageView; // 用于显示视频流或拍摄的照片
    @FXML
    private Button btnCapture; // 拍照按钮
    @FXML
    private Button btnRetake;  // 重拍按钮
    @FXML
    private Button btnConfirm; // 确定按钮
    @FXML
    private Button btnCancel;  // 取消按钮

    // 内部变量
    private Webcam webcam;
    private Thread cameraThread;
    private Image capturedImage; // 存储拍摄到的照片
    private EmployeeDetailController detailController; // 用于回传数据的控制器
    private final double PREVIEW_W = 640.0;
    private final double PREVIEW_H = 480.0;

    private String photoBase64;

    public String getPhotoBase64() {
        return photoBase64;
    }

    // 假设父控制器实例通过外部方法注入
    private EmployeeDetailController parentController;

    // --- 初始化和生命周期 ---

    public Image getCapturedImage() {
        return capturedImage;
    }

    @FXML
    public void initialize() {
        // 初始状态：显示拍照和取消，隐藏重拍和确定
        btnCapture.setVisible(true);
        btnRetake.setVisible(false);
        btnConfirm.setVisible(false);
        btnCancel.setVisible(true);
        // 确保 videoImageView 的尺寸与预览区域匹配
        videoImageView.setFitWidth(PREVIEW_W);
        videoImageView.setFitHeight(PREVIEW_H);
        videoImageView.setPreserveRatio(true);

        // 初始化摄像头
        startCamera();
    }

    public void shutdown() {
        closeStage();
    }

    // ------------------------------------
    // --- 摄像头控制 ---
    // ------------------------------------
    private void startCamera() {
        // ... 摄像头初始化逻辑 (省略)
        webcam = Webcam.getDefault();
        webcam.setViewSize(new Dimension((int) PREVIEW_W, (int) PREVIEW_H));
        webcam.open();

        // 启动新的线程来读取视频帧
        cameraThread = new Thread(() -> {
            while (true) {
                BufferedImage image = webcam.getImage();
                if (image != null) {
                    Image fxImage = SwingFXUtils.toFXImage(image, null);
                    Platform.runLater(() -> {
                        // 仅在未拍照状态下更新视频流
                        if (btnCapture.isVisible()) {
                            videoImageView.setImage(fxImage);
                        }
                    });
                }
                try {
                    Thread.sleep(50); // 控制帧率
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        cameraThread.setDaemon(true);
        cameraThread.start();
    }

    // ------------------------------------
    // --- 按钮事件逻辑 ---
    // ------------------------------------

    @FXML
    private void handleCapture() {
        // 停止视频流的视觉更新
        BufferedImage image = webcam.getImage();
        if (image != null) {
            // 拍摄成功，更新 ImageView 显示拍摄的照片
            capturedImage = SwingFXUtils.toFXImage(image, null);
            videoImageView.setImage(capturedImage);

            // 切换按钮状态：拍照 -> 重拍 + 确定
            btnCapture.setVisible(false);
            btnRetake.setVisible(true);
            btnConfirm.setVisible(true);
        }
    }

    @FXML
    private void handleRetake() {
        // 清除拍摄的照片
        capturedImage = null;

        // 恢复视频流显示 (因为 cameraThread 仍在运行，只需更新按钮状态，让它继续在 Platform.runLater 中更新 videoImageView)

        // 切换按钮状态：重拍 + 确定 -> 拍照
        btnCapture.setVisible(true);
        btnRetake.setVisible(false);
        btnConfirm.setVisible(false);
    }

    @FXML
    private void handleCancel() {
        closeStage();
    }

    /**
     * 关闭摄像头和窗口
     */
    private void closeStage() {
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }
        if (cameraThread != null) {
            cameraThread.interrupt();
        }
        // 获取当前 Stage 并关闭
        Stage stage = (Stage) btnCancel.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void handleConfirm() {
        if (capturedImage == null) {
            closeStage();
            return;
        }

        // 1. 圖片處理和壓縮 (省略細節)
        BufferedImage originalImage = SwingFXUtils.fromFXImage(capturedImage, null);
        BufferedImage resizedImage = ImageConverter.resizeImage(originalImage, 720, 480);

        // 2. 設置 Base64 字符串
        this.photoBase64 = ImageConverter.encodeImageToBase64(resizedImage); // <--- 設置給字段

        // 3. 關閉窗口
        closeStage();
    }
}