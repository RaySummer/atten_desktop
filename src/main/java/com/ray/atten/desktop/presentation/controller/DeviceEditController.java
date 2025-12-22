package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.model.Device;
import com.ray.atten.desktop.service.DeviceService;
import com.ray.atten.desktop.utils.CustomAlertDialog;
import com.ray.atten.desktop.utils.LoadingManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;

@Component
public class DeviceEditController {

    @Autowired
    private DeviceService deviceService;

    @FXML
    private Label formTitle;
    @FXML
    private TextField txtSn, txtAlias, txtLocation, txtModel, txtIp;
    @FXML
    private CheckBox chkActive;
    private Runnable onCloseRequest;
    private DeviceManagementController parentController;
    private Device currentDevice;

    @Autowired
    private LoadingManager loadingManager;

    public void setParentController(DeviceManagementController parent) {
        this.parentController = parent;
    }

    public void setOnCloseRequest(Runnable onCloseRequest) {
        this.onCloseRequest = onCloseRequest;
    }

    public void initData(Device device) {
        this.currentDevice = device;
        if (device != null) {
            formTitle.setText("修改设备 - " + device.getDeviceSn());
            txtSn.setText(device.getDeviceSn());
            txtSn.setEditable(false); // 修改时 SN 通常不可变
            txtAlias.setText(device.getAlias());
            txtLocation.setText(device.getLocation());
            txtModel.setText(device.getModel());
            txtIp.setText(device.getIpAddress());
            chkActive.setSelected(device.getActive() != null && device.getActive());
        } else {
            formTitle.setText("新增设备");
            this.currentDevice = new Device(); // 新建对象
            clearForm();
        }
    }

    private void clearForm() {
        txtSn.clear();
        txtSn.setEditable(true);
        txtAlias.clear();
        txtLocation.clear();
        txtModel.clear();
        txtIp.clear();
        chkActive.setSelected(true);
    }

    @FXML
    private void handleSave() {
        // 1. 显示 Loading
        loadingManager.show("正在获取设备数据...");
        // 1. 数据收集与简单验证
        if (txtSn.getText().trim().isEmpty()) {
            CustomAlertDialog.showWarning("校验失败", "设备序列号不能为空");
            return;
        }

        // 更新对象模型
        currentDevice.setDeviceSn(txtSn.getText().trim());
        currentDevice.setAlias(txtAlias.getText().trim());
        currentDevice.setLocation(txtLocation.getText().trim());
        currentDevice.setModel(txtModel.getText().trim());
        currentDevice.setIpAddress(txtIp.getText().trim());
        currentDevice.setActive(chkActive.isSelected());

        // 2. 异步调用 Service 保存
        Task<Boolean> saveTask = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                // 调用 DeviceService 中的方法
                return deviceService.saveOrUpdateDevice(currentDevice);
            }
        };

        saveTask.setOnSucceeded(e -> {
            // 2. 成功后隐藏
            loadingManager.hide();
            if (saveTask.getValue()) {
                // 保存成功后的处理
                CustomAlertDialog.showInfo("成功", "设备信息已保存");

                // 核心：刷新父级列表
                if (parentController != null) {
                    parentController.loadDeviceData();
                }

                // 关闭抽屉
                handleClose();
            } else {
                CustomAlertDialog.showError("失败", "服务器保存数据失败，请检查后端日志");
            }
        });

        saveTask.setOnFailed(e -> {
            Throwable exception = saveTask.getException();
            if (exception instanceof SocketTimeoutException || exception.getMessage().contains("timeout")) {
                // 3. 超时显示重试按钮，重试逻辑就是再次调用本方法
                loadingManager.showTimeout(() -> handleSave());
            } else {
                loadingManager.hide();
                exception.printStackTrace();
                CustomAlertDialog.showError("系统错误", "网络请求异常: " + exception.getMessage());
            }
        });

        // 启动后台线程
        new Thread(saveTask).start();
    }

    @FXML
    private void handleClose() {
        if (onCloseRequest != null) onCloseRequest.run();
    }
}