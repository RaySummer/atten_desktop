package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.dto.FingerprintResult;
import com.ray.atten.desktop.model.OaEmployee;
import com.ray.atten.desktop.utils.AppConstants;
import com.ray.atten.desktop.utils.CustomAlertDialog;
import com.ray.atten.desktop.utils.ImageConverter;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Component
public class EmployeeDetailController {

    // 圖像控件
    @FXML
    private ImageView photoImageView;

    // 【新增】指紋 ImageView 控件
    @FXML
    private ImageView fingerprint1ImageView;
//    @FXML private ImageView fingerprint2ImageView;

    // 基本信息 Label 控件
    @FXML
    private Label pinLabel;
    @FXML
    private Label nameLabel;
    @FXML
    private Label deptLabel;
    @FXML
    private Label entryDateLabel;
    @FXML
    private Label statusLabel;

    // 操作按鈕
    @FXML
    private Button captureButton;
    @FXML
    private Button uploadButton;
    // 【新增】指紋操作按鈕和底部按鈕
    @FXML
    private Button uploadFingerprintButton;
    @FXML
    private Button confirmButton;
    @FXML
    private Button cancelButton;

    private Image currentPhoto;
    // 【新增】用於存儲當前指紋圖像數據
    private String currentFingerprint1Base64;
    private String currentFingerprint2Base64;

    private OaEmployee employee; // 存儲完整員工對象

    @Autowired
    private ApplicationContext springContext;

    // 定义固定的窗口尺寸
    private static final double WINDOW_WIDTH = 600.0;
    private static final double WINDOW_HEIGHT = 720.0;

    // 默認圖片常量
    private final Image DEFAULT_AVATAR = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/default_avatar.png")));
    // 【新增】默認指紋圖片常量
    private final Image DEFAULT_FINGERPRINT = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/default_fingerprint.png")));


    @FXML
    public void initialize() {
        // 初始設置佔位圖
        photoImageView.setImage(DEFAULT_AVATAR);
        fingerprint1ImageView.setImage(DEFAULT_FINGERPRINT); // 設置默認指紋圖
//        fingerprint2ImageView.setImage(DEFAULT_FINGERPRINT); // 設置默認指紋圖

        this.currentPhoto = null;
        this.currentFingerprint1Base64 = null;
        this.currentFingerprint2Base64 = null;

        captureButton.setDisable(false);
    }

    // 設置當前員工信息的方法（由 MainController 調用）
    public void setEmployeeInfo(OaEmployee employee) {
        this.employee = employee;

        if (employee != null) {
            // ... (基本信息 Label 設置邏輯保持不變) ...
            pinLabel.setText(employee.getPin() != null ? employee.getPin() : "[N/A]");
            nameLabel.setText(employee.getName() != null ? employee.getName() : "[N/A]");
            entryDateLabel.setText(employee.getEntryDate() != null ? AppConstants.dateTimeFormatter(employee.getEntryDate()) : AppConstants.dateTimeFormatter(LocalDateTime.now()));
            statusLabel.setText(employee.getInService() ? "在職" : "離職");

            // 【照片數據處理】
            String photoBase64 = employee.getPhotoBase64();
            if (photoBase64 != null && !photoBase64.isEmpty()) {
                Image employeeImage = ImageConverter.base64ToImage(photoBase64);
                if (employeeImage != null) {
                    photoImageView.setImage(employeeImage);
                    this.currentPhoto = employeeImage;
                } else {
                    photoImageView.setImage(DEFAULT_AVATAR);
                    this.currentPhoto = null;
                }
            } else {
                photoImageView.setImage(DEFAULT_AVATAR);
                this.currentPhoto = null;
            }

            // 【新增】指紋數據處理 (假設 OaEmployee 有 fingerprint1Base64 和 fingerprint2Base64 字段)
            setupFingerprintImage(employee.getFingerprint(), fingerprint1ImageView, true);
//            setupFingerprintImage(employee.getFingerprint(), fingerprint2ImageView, false);
        }
    }

    /**
     * 輔助方法：設置指紋圖像和 Base64 數據。
     *
     * @param base64Data         指紋的 Base64 數據
     * @param imageView          目標 ImageView
     * @param isFirstFingerprint 是否為第一個指紋（用於存儲 Base64）
     */
    private void setupFingerprintImage(String base64Data, ImageView imageView, boolean isFirstFingerprint) {
        if (base64Data != null && !base64Data.isEmpty()) {
            Image fingerprintImage = ImageConverter.base64ToImage(base64Data);
            if (fingerprintImage != null) {
                imageView.setImage(fingerprintImage);
                if (isFirstFingerprint) {
                    this.currentFingerprint1Base64 = base64Data;
                } else {
                    this.currentFingerprint2Base64 = base64Data;
                }
                return;
            }
        }
        imageView.setImage(DEFAULT_FINGERPRINT);
        if (isFirstFingerprint) {
            this.currentFingerprint1Base64 = null;
        } else {
            this.currentFingerprint2Base64 = null;
        }
    }


    /**
     * 處理上傳照片按鈕的點擊事件：打開文件選擇器。
     */
    @FXML
    private void handleUploadPhoto() {
        // ... (保持不變) ...
        Stage stage = (Stage) uploadButton.getScene().getWindow();
        FileChooser fileChooser = new FileChooser();

        // 設置文件過濾器
        FileChooser.ExtensionFilter imageFilter =
                new FileChooser.ExtensionFilter("圖片文件 (*.jpg, *.jpeg, *.png, *.gif)",
                        "*.jpg", "*.jpeg", "*.png", "*.gif");
        fileChooser.getExtensionFilters().add(imageFilter);

        // 設置標題
        fileChooser.setTitle("選擇員工照片");

        // 只能選擇單個文件
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            try {
                // 將選中的文件轉換為 Image 對象
                Image image = new Image(new FileInputStream(file));

                // 回傳到 ImageView
                photoImageView.setImage(image);

                // 存儲照片對象以備後續 BASE64 轉換和傳輸
                this.currentPhoto = image;

                System.out.println("成功上傳文件: " + file.getAbsolutePath());

            } catch (FileNotFoundException e) {
                new Alert(Alert.AlertType.ERROR, "文件未找到: " + e.getMessage()).showAndWait();
            }
        }
    }

    /**
     * 處理拍照按鈕的點擊事件：啟動異步任務調用攝像頭。
     */
    @FXML
    private void handleCapturePhoto() {
        // ... (保持不變) ...
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/CameraPreviewView.fxml"));

            // 確保 Spring 創建 CameraPreviewController
            loader.setControllerFactory(springContext::getBean);

            Parent root = loader.load();
            CameraPreviewController cameraController = loader.getController();

            Stage cameraStage = new Stage();
            cameraStage.setTitle("攝像頭預覽");
            cameraStage.setScene(new Scene(root));
            cameraStage.initModality(Modality.APPLICATION_MODAL);
            cameraStage.setWidth(660.0);
            cameraStage.setHeight(700.0);

            // 2. 禁用窗口大小调整功能
            cameraStage.setResizable(false);

            // 關閉 Stage 時，強制停止攝像頭線程 (假設 shutdown 方法已實現)
            cameraStage.setOnHidden(e -> cameraController.shutdown());

            cameraStage.showAndWait(); // 阻塞等待結果

            // 處理返回的結果：獲取 Base64 字符串
            String photoBase64 = cameraController.getPhotoBase64();

            if (photoBase64 != null && !photoBase64.isEmpty()) {
                Image employeeImage = ImageConverter.base64ToImage(photoBase64);

                if (employeeImage != null) {
                    photoImageView.setImage(employeeImage);
                    this.currentPhoto = employeeImage;
                } else {
                    photoImageView.setImage(DEFAULT_AVATAR);
                    this.currentPhoto = null;
                }

            } else {
                photoImageView.setImage(DEFAULT_AVATAR);
                this.currentPhoto = null;
            }

        } catch (IOException e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "無法打開攝像頭窗口: " + e.getMessage()).showAndWait();
        }
    }

    /**
     * 處理點擊圖片放大事件：彈出一個新的 Stage 顯示原始大小或放大後的圖片。
     */
    @FXML
    private void handleImageClickToZoom(MouseEvent event) {
        // ... (保持不變) ...
        if (this.currentPhoto == null) {
            CustomAlertDialog.showWarning("操作提示", "請先錄入照片！");
            return;
        }

        // 1. 創建一個新的 Stage 彈窗
        Stage zoomStage = new Stage();
        zoomStage.setTitle("照片預覽 (點擊關閉)");
        zoomStage.setWidth(WINDOW_WIDTH);
        zoomStage.setHeight(WINDOW_HEIGHT);
        zoomStage.setResizable(false);

        // 2. 創建一個新的 ImageView 顯示照片
        ImageView zoomedImageView = new ImageView(this.currentPhoto);
        zoomedImageView.setFitWidth(WINDOW_WIDTH);
        zoomedImageView.setFitHeight(WINDOW_HEIGHT);
        zoomedImageView.setPreserveRatio(true);
        zoomedImageView.setSmooth(true);

        // 4. 使用 StackPane 作为根容器，实现居中
        StackPane rootPane = new StackPane();
        rootPane.setPrefSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        rootPane.getChildren().add(zoomedImageView);
        StackPane.setAlignment(zoomedImageView, Pos.CENTER);

        // 5. 創建 Scene 並設置 Stage
        Scene scene = new Scene(rootPane);
        zoomStage.setScene(scene);

        // 6. 點擊彈窗即關閉
        scene.setOnMouseClicked(e -> zoomStage.close());

        zoomStage.show();
    }

    /**
     * 【新增】處理點擊指紋圖片放大事件。
     */
    @FXML
    private void handleFingerprintClickToZoom(MouseEvent event) {
        ImageView source = (ImageView) event.getSource();
        Image image = source.getImage();

        if (image == DEFAULT_FINGERPRINT) {
            CustomAlertDialog.showWarning("操作提示", "該指紋位無數據，無法放大！");
            return;
        }

        // 創建放大 Stage (邏輯與 handleImageClickToZoom 類似，可以提取為通用方法)
        Stage zoomStage = new Stage();
        zoomStage.setTitle("指紋預覽 (點擊關閉)");

        ImageView zoomedImageView = new ImageView(image);
        zoomedImageView.setPreserveRatio(true);
        zoomedImageView.setFitWidth(400.0);
        zoomedImageView.setFitHeight(400.0);

        StackPane rootPane = new StackPane(zoomedImageView);
        rootPane.setPrefSize(400.0, 400.0);

        Scene scene = new Scene(rootPane);
        zoomStage.setScene(scene);
        scene.setOnMouseClicked(e -> zoomStage.close());

        zoomStage.show();
    }

    /**
     * 處理上傳指紋按鈕點擊事件
     */
    @FXML
    private void handleUploadFingerprint() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/FingerprintCaptureView.fxml"));

            // 确保 Spring 能够创建 FingerprintCaptureController (Scope="prototype")
            loader.setControllerFactory(springContext::getBean);

            Parent root = loader.load();
            FingerprintCaptureController captureController = loader.getController();

            // 传递当前员工信息
            captureController.setEmployeeInfo(this.employee);

            Stage captureStage = new Stage();
            captureStage.setTitle("員工指紋錄入");
            captureStage.setScene(new Scene(root));
            captureStage.initModality(Modality.APPLICATION_MODAL); // 设置为模态窗口
            captureStage.setWidth(600.0);
            captureStage.setHeight(550.0);
            captureStage.setResizable(false);

            // 确保在窗口被关闭时释放指纹仪资源
            captureStage.setOnHidden(e -> captureController.shutdown());

            captureStage.showAndWait(); // 阻塞等待结果

            // ==========================================================
            // 處理返回的結果：獲取采集到的指紋數據 (FingerprintResult)
            // ==========================================================
            FingerprintResult result = (FingerprintResult) captureStage.getUserData();

            if (result != null) {
                // 成功采集并保存

                boolean fingerprint1Exists = employee.getFingerprint() != null && !employee.getFingerprint().isEmpty();
                // 假设 employee.getFingerprint2Base64() 是第二个指纹的数据源
                // boolean fingerprint2Exists = employee.getFingerprint2Base64() != null && !employee.getFingerprint2Base64().isEmpty();

                // 1. 决定存储位置 (如果第一个指纹为空，则存入第一个位置)
                if (!fingerprint1Exists) {

                    // 指纹1为空，直接录入
                    employee.setFingerprint(result.getTemplateBase64());
                    fingerprint1ImageView.setImage(result.getFingerprintImage());
                    CustomAlertDialog.showInfo("錄入成功", "指紋已成功錄入到第一個指紋位，請點擊【確定】保存數據。");

                }
                // 2. 如果指纹1不为空，询问是否替换
                else {

                    // 【当前只处理一个指纹位，故直接询问替换指纹1】

                    // 弹窗提示是否替换第一个指纹
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("指紋替換確認");
                    alert.setHeaderText("第一個指紋位已存在數據。");
                    alert.setContentText("是否要用新采集的指紋替換現有的指紋？");

                    ButtonType buttonYes = new ButtonType("替換", ButtonBar.ButtonData.YES);
                    ButtonType buttonNo = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);

                    alert.getButtonTypes().setAll(buttonYes, buttonNo);

                    Optional<ButtonType> option = alert.showAndWait();

                    if (option.isPresent() && option.get() == buttonYes) {
                        // 用户选择替换第一个指纹
                        employee.setFingerprint(result.getTemplateBase64());
                        fingerprint1ImageView.setImage(result.getFingerprintImage());
                        CustomAlertDialog.showInfo("替換成功", "指紋已成功替換第一個指紋位，請點擊【確定】保存數據。");
                    }
                    // 用户选择取消 (点否)，不做任何处理

                }

            /*
            // =================================================================
            // 【未来扩展：两个指纹位的逻辑，目前保持注释】
            // =================================================================

            else if (!fingerprint2Exists) {

                // 指纹2为空，录入到指纹2
                // employee.setFingerprint2Base64(result.getTemplateBase64());
                // fingerprint2ImageView.setImage(result.getFingerprintImage());
                // CustomAlertDialog.showInfo("錄入成功", "指紋已成功錄入到第二個指紋位，請點擊【確定】保存數據。");

            } else {

                // 两个指纹位都已存在数据，询问替换哪个
                // Alert replaceAlert = new Alert(Alert.AlertType.CONFIRMATION);
                // replaceAlert.setTitle("指紋替換確認");
                // replaceAlert.setHeaderText("兩個指紋位均已存在數據。");
                // replaceAlert.setContentText("請選擇您要替換的指紋位：");

                // ButtonType replace1 = new ButtonType("替換指紋 1", ButtonBar.ButtonData.YES);
                // ButtonType replace2 = new ButtonType("替換指紋 2", ButtonBar.ButtonData.NO);
                // ButtonType cancel = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);

                // replaceAlert.getButtonTypes().setAll(replace1, replace2, cancel);

                // Optional<ButtonType> replaceOption = replaceAlert.showAndWait();

                // if (replaceOption.isPresent()) {
                //     if (replaceOption.get() == replace1) {
                //         employee.setFingerprint(result.getTemplateBase64());
                //         fingerprint1ImageView.setImage(result.getFingerprintImage());
                //         CustomAlertDialog.showInfo("替換成功", "指紋 1 已更新。請點擊【確定】保存數據。");
                //     } else if (replaceOption.get() == replace2) {
                //         employee.setFingerprint2Base64(result.getTemplateBase64());
                //         fingerprint2ImageView.setImage(result.getFingerprintImage());
                //         CustomAlertDialog.showInfo("替換成功", "指紋 2 已更新。請點擊【確定】保存數據。");
                //     }
                // }

                // CustomAlertDialog.showWarning("錄入已滿", "兩個指紋位均已存在數據。");
            }
            */

            }

        } catch (Exception e) {
            e.printStackTrace();
            CustomAlertDialog.showError("指紋窗口錯誤", "無法打開指紋采集窗口: " + e.getMessage());
        }
    }

    /**
     * 【新增】處理底部確定按鈕。
     */
    @FXML
    private void handleConfirm() {
        // 執行保存邏輯，並關閉窗口
        // 1. 將 currentPhoto 轉為 Base64
        // 2. 將 currentFingerprintBase64(s) 存入 employee 對象
        // 3. 調用服務保存

        // 關閉窗口
        ((Stage) confirmButton.getScene().getWindow()).close();
    }

    /**
     * 【新增】處理底部取消按鈕。
     */
    @FXML
    private void handleCancel() {
        // 關閉窗口，不進行任何操作
        ((Stage) cancelButton.getScene().getWindow()).close();
    }

}