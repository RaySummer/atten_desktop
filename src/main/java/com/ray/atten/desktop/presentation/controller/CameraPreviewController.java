package com.ray.atten.desktop.presentation.controller;

import com.github.sarxos.webcam.Webcam;
import com.ray.atten.desktop.utils.ImageConverter;
import com.ray.atten.desktop.utils.ImageCropperTool;
import com.ray.atten.desktop.utils.LoadingManager;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class CameraPreviewController {

    @FXML
    private VBox rootVBox;
    @FXML
    private ImageView videoImageView;
    @FXML
    private Pane cropContainer;
    @FXML
    private Button btnCapture, btnRetake, btnConfirm, btnCancel;
    @FXML
    private Region flashPane;

    private ImageCropperTool cropperTool;
    private Webcam webcam;
    private Thread cameraThread;
    private Image capturedImage;
    private String photoBase64;
    private boolean confirmed = false;
    private final AtomicBoolean isClosing = new AtomicBoolean(false);
    @Autowired
    private LoadingManager loadingManager;


    public boolean isConfirmed() {
        return confirmed;
    }

    public String getPhotoBase64() {
        return photoBase64;
    }

    @FXML
    public void initialize() {
        loadingManager.hide();
        videoImageView.setPreserveRatio(true);
        // 重要：先建立工具类实例
        if (cropContainer != null) {
            cropperTool = new ImageCropperTool(cropContainer);
        }
        updateUIState(false);
        startCamera();
    }

    private void updateUIState(boolean isCaptured) {
        // 安全校验，防止注入失败导致的 NPE
        if (btnCapture == null || btnConfirm == null) return;

        btnCapture.setVisible(!isCaptured);
        btnCapture.setManaged(!isCaptured);
        btnRetake.setVisible(isCaptured);
        btnRetake.setManaged(isCaptured);
        btnConfirm.setVisible(isCaptured);
        btnConfirm.setManaged(isCaptured);

        if (cropperTool != null) {
            if (isCaptured) {
                // 1. 先激活
                cropperTool.activate();

                // 2. 修改大框的初始尺寸（高度铺满，宽度稍微窄一点符合工牌比例）
                Platform.runLater(() -> {
                    double viewW = videoImageView.getBoundsInParent().getWidth();
                    double viewH = videoImageView.getBoundsInParent().getHeight();

                    javafx.scene.shape.Rectangle rect = cropperTool.getSelection();
                    if (rect != null) {
                        rect.setY(0);
                        rect.setHeight(viewH); // 铺满窗口全高
                        double defaultWidth = viewW * 0.55; // 默认给一个合适的工牌宽度
                        rect.setX((viewW - defaultWidth) / 2); // 居中
                        rect.setWidth(defaultWidth);

                        // 【极其重要的一步】：改完宽高后，手动让工具类重新刷一次遮罩！
                        cropperTool.refresh();
                    }
                });
            } else {
                cropperTool.deactivate();
            }
        }
    }

    @FXML
    private void handleCapture() {
        if (webcam != null && webcam.isOpen()) {
            runFlashAnimation();
            BufferedImage image = webcam.getImage();
            if (image != null) {
                capturedImage = SwingFXUtils.toFXImage(image, null);
                videoImageView.setImage(capturedImage);
                updateUIState(true);
            }
        }
    }

    @FXML
    private void handleConfirm() {
        if (capturedImage != null && cropperTool != null) {
            try {
                javafx.scene.shape.Rectangle rect = cropperTool.getSelection();
                BufferedImage fullBI = SwingFXUtils.fromFXImage(capturedImage, null);

                // 获取 ImageView 此时真实的显示宽高 (考虑比例自适应)
                double viewW = videoImageView.getBoundsInParent().getWidth();
                double viewH = videoImageView.getBoundsInParent().getHeight();

                double ratioX = fullBI.getWidth() / viewW;
                double ratioY = fullBI.getHeight() / viewH;

                // --- 【核心修改二】：从业务上杜绝高度误差 ---
                // 宽度和 X 坐标依然根据用户的拖拽去计算
                int x = (int) (rect.getX() * ratioX);
                int w = (int) (rect.getWidth() * ratioX);

                // 高度固定为窗口/图片的满高，Y 坐标直接锁死在最顶层 0
                int y = 0;
                int h = fullBI.getHeight();

                // 强制修正坐标，防止 getSubimage 坐标越界异常
                x = Math.max(0, Math.min(x, fullBI.getWidth() - 1));
                w = Math.max(1, Math.min(w, fullBI.getWidth() - x));

                BufferedImage cropped = fullBI.getSubimage(x, y, w, h);
                this.photoBase64 = ImageConverter.encodeImageToBase64(ImageConverter.resizeImage(cropped, 480, 480));
                this.confirmed = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        closeStage();
    }

    @FXML
    private void handleRetake() {
        capturedImage = null;
        updateUIState(false);
    }

    @FXML
    private void handleCancel() {
        this.confirmed = false;
        closeStage();
    }

    public void shutdown() {
        closeStage();
    }

    private void closeStage() {
        if (cameraThread != null) {
            cameraThread.interrupt();
        }
        Platform.runLater(() -> {
            if (webcam != null && webcam.isOpen()) {
                webcam.close();
            }
            Stage stage = (Stage) btnCancel.getScene().getWindow();
            stage.close();
        });
    }

    private void startCamera() {
        try {
            webcam = Webcam.getDefault();
            if (webcam != null) {
                webcam.setViewSize(new Dimension(640, 480));
                webcam.open();
                cameraThread = new Thread(() -> {
                    while (!Thread.interrupted()) {
                        if (webcam != null && webcam.isOpen()) {
                            BufferedImage image = webcam.getImage();
                            if (image != null) {
                                Image fxImage = SwingFXUtils.toFXImage(image, null);
                                Platform.runLater(() -> {
                                    if (btnCapture.isVisible()) videoImageView.setImage(fxImage);
                                });
                            }
                        }
                        try {
                            Thread.sleep(40);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                });
                cameraThread.setDaemon(true);
                cameraThread.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void runFlashAnimation() {
        FadeTransition flash = new FadeTransition(Duration.millis(100), flashPane);
        flash.setFromValue(0.0);
        flash.setToValue(0.8);
        flash.setCycleCount(2);
        flash.setAutoReverse(true);
        flash.play();
    }
}