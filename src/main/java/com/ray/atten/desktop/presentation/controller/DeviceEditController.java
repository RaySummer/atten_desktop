package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.model.Company;
import com.ray.atten.desktop.model.Device;
import com.ray.atten.desktop.service.CompanyService;
import com.ray.atten.desktop.service.DeviceService;
import com.ray.atten.desktop.utils.CustomAlertDialog;
import com.ray.atten.desktop.utils.LoadingManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class DeviceEditController {

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private CompanyService companyService; // 新增：公司服务

    @FXML
    private Label formTitle;
    @FXML
    private TextField txtSn, txtAlias, txtLocation, txtModel, txtIp;
    @FXML
    private CheckBox chkActive;

    @FXML
    private ListView<Company> companyListView; // 新增：与管理员详情风格一致的列表

    private Runnable onCloseRequest;
    private DeviceManagementController parentController;
    private Device currentDevice;

    @Autowired
    private LoadingManager loadingManager;

    // 绑定列表数据
    private final ObservableList<Company> companyObservableList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // 设置 ListView 单元格为复选框模式
        // 关键点：增加一个 StringConverter，告诉 ListView 如何把 Company 对象转成字符串显示
        companyListView.setCellFactory(CheckBoxListCell.forListView(
                Company::selectedProperty,
                new javafx.util.StringConverter<Company>() {
                    @Override
                    public String toString(Company company) {
                        // 这里返回你想在列表里显示的字段，比如 getName() 或 getCompanyName()
                        return company == null ? "" : company.getName();
                    }

                    @Override
                    public Company fromString(String string) {
                        return null; // 这里通常不需要实现，除非列表可编辑
                    }
                }
        ));

        companyListView.setItems(companyObservableList);
    }

    public void setParentController(DeviceManagementController parent) {
        this.parentController = parent;
    }

    public void setOnCloseRequest(Runnable onCloseRequest) {
        this.onCloseRequest = onCloseRequest;
    }

    /**
     * 初始化数据入口
     */
    public void initData(Device device) {
        this.currentDevice = device;

        // 1. 首先异步加载所有公司，加载后再进行回显
        loadCompanyListAndSelection(device);

        if (device != null) {
            formTitle.setText("修改设备 - " + device.getDeviceSn());
            txtSn.setText(device.getDeviceSn());
            txtSn.setEditable(false);
            txtAlias.setText(device.getAlias());
            txtLocation.setText(device.getLocation());
            txtModel.setText(device.getModel());
            txtIp.setText(device.getIpAddress());
            chkActive.setSelected(device.getActive() != null && device.getActive());
        } else {
            formTitle.setText("新增设备");
            this.currentDevice = new Device();
            clearForm();
        }
    }

    /**
     * 加载公司列表并处理多选回显
     */
    private void loadCompanyListAndSelection(Device device) {
        Task<List<Company>> fetchTask = new Task<List<Company>>() {
            @Override
            protected List<Company> call() throws Exception {
                return companyService.getAllCompanies();
            }
        };

        fetchTask.setOnSucceeded(e -> {
            List<Company> allCompanies = fetchTask.getValue();

            // --- 核心修改：回显逻辑由 Name 匹配改为 UUID 匹配 ---
            // 假设 device 对象现在包含 getCompanyUuids() 方法，返回 List<String>
            List<UUID> boundUuids = (device != null) ? device.getCompanyUuids() : null;

            if (boundUuids != null && !boundUuids.isEmpty()) {
                allCompanies.forEach(c -> {
                    // 根据 UUID 判断是否勾选
                    c.setSelected(boundUuids.contains(c.getUuid()));
                });
            } else {
                allCompanies.forEach(c -> c.setSelected(false));
            }

            companyObservableList.setAll(allCompanies);
        });

        fetchTask.setOnFailed(e -> {
            fetchTask.getException().printStackTrace();
            CustomAlertDialog.showError("加载失败", "无法获取公司清单");
        });

        new Thread(fetchTask).start();
    }

    private void clearForm() {
        txtSn.clear();
        txtSn.setEditable(true);
        txtAlias.clear();
        txtLocation.clear();
        txtModel.clear();
        txtIp.clear();
        chkActive.setSelected(true);
        companyObservableList.forEach(c -> c.setSelected(false));
    }

    @FXML
    private void handleSave() {
        if (txtSn.getText().trim().isEmpty()) {
            CustomAlertDialog.showWarning("校验失败", "设备序列号不能为空");
            return;
        }

        loadingManager.show("正在提交数据...");

        // 1. 更新对象模型（基础字段）
        currentDevice.setDeviceSn(txtSn.getText().trim());
        currentDevice.setAlias(txtAlias.getText().trim());
        currentDevice.setLocation(txtLocation.getText().trim());
        currentDevice.setModel(txtModel.getText().trim());
        currentDevice.setIpAddress(txtIp.getText().trim());
        currentDevice.setActive(chkActive.isSelected());

        // --- 核心修改 2：收集 UUID 列表而非拼接字符串 ---
        List<UUID> selectedUuids = companyObservableList.stream()
                .filter(Company::isSelected)
                .map(Company::getUuid) // 获取 UUID
                .collect(Collectors.toList());

        // 确保 Device 类中有 setCompanyUuids 方法
        currentDevice.setCompanyUuids(selectedUuids);

        // 3. 异步调用 Service 保存
        Task<Boolean> saveTask = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                // 此时 device 序列化为 JSON 时会包含 "companyUuids": ["uuid1", "uuid2"]
                return deviceService.saveOrUpdateDevice(currentDevice);
            }
        };

        saveTask.setOnSucceeded(e -> {
            loadingManager.hide();
            if (saveTask.getValue()) {
                CustomAlertDialog.showInfo("成功", "设备信息已保存");
                if (parentController != null) {
                    parentController.loadDeviceData();
                }
                handleClose();
            } else {
                CustomAlertDialog.showError("失败", "服务器保存数据失败");
            }
        });

        saveTask.setOnFailed(e -> {
            loadingManager.hide();
            Throwable exception = saveTask.getException();
            if (exception instanceof SocketTimeoutException || exception.getMessage().contains("timeout")) {
                loadingManager.showTimeout(this::handleSave);
            } else {
                exception.printStackTrace();
                CustomAlertDialog.showError("系统错误", "网络异常 ");
            }
        });

        new Thread(saveTask).start();
    }

    @FXML
    private void handleClose() {
        if (onCloseRequest != null) onCloseRequest.run();
    }
}