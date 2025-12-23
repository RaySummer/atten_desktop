package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.dto.DeviceRequest;
import com.ray.atten.desktop.model.Device;
import com.ray.atten.desktop.service.DeviceService;
import com.ray.atten.desktop.utils.CustomAlertDialog;
import com.ray.atten.desktop.utils.LoadingManager;
import javafx.animation.TranslateTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

@Component
public class DeviceManagementController {

    @Autowired
    private ApplicationContext springContext;

    @FXML
    private AnchorPane drawerPane;
    @FXML
    private VBox drawerContent;
    @FXML
    private StackPane detailContainer;
    @FXML
    private AnchorPane tableContainer;

    @FXML
    private TextField searchSn;
    @FXML
    private ComboBox<String> activeFilter;
    @FXML
    private Label statusLabel;
    @FXML
    private TableColumn<Device, Integer> rowNumberColumn;
    @FXML
    private TableColumn<Device, String> snCol;
    @FXML
    private TableColumn<Device, String> aliasCol;
    @FXML
    private TableColumn<Device, String> locCol;
    @FXML
    private TableColumn<Device, String> modelCol;
    @FXML
    private TableColumn<Device, String> ipCol;
    @FXML
    private TableColumn<Device, Boolean> activeCol;
    @FXML
    private TableView<Device> deviceTable;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private LoadingManager loadingManager;

    @FXML
    public void initialize() {
        createTableViewStructure();
        setupActionColumn();

        activeFilter.setItems(FXCollections.observableArrayList("全部", "已激活", "未激活"));
        activeFilter.setValue("全部");

        // 將表格裝載到容器
        tableContainer.getChildren().add(deviceTable);
        AnchorPane.setTopAnchor(deviceTable, 0.0);
        AnchorPane.setBottomAnchor(deviceTable, 0.0);
        AnchorPane.setLeftAnchor(deviceTable, 0.0);
        AnchorPane.setRightAnchor(deviceTable, 0.0);

        loadDeviceData();
    }

    private void openViewInDrawer(String fxmlPath, Device data) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/DeviceEditFormView.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Node node = loader.load();

            Object controller = loader.getController();
            if (controller instanceof DeviceEditController) {
                ((DeviceEditController) controller).setParentController(this);
                ((DeviceEditController) controller).initData(data);
                // 這裡假設 DeviceEditController 也有 setOnCloseRequest
                ((DeviceEditController) controller).setOnCloseRequest(this::closeDrawer);
            }

            detailContainer.getChildren().setAll(node);
            showDrawer();
        } catch (IOException e) {
            e.printStackTrace();
            CustomAlertDialog.showError("", "无法加载界面: " + fxmlPath);
        }
    }

    private void showDrawer() {
        if (drawerPane == null) return;
        drawerPane.setVisible(true);
        drawerPane.setMouseTransparent(false);

        TranslateTransition tt = new TranslateTransition(Duration.millis(500), drawerContent);
        // 與 EmployeeList 一致，使用 550 作為寬度參考
        tt.setFromX(550);
        tt.setToX(0);
        tt.play();
    }

    @FXML
    public void closeDrawer() {
        TranslateTransition tt = new TranslateTransition(Duration.millis(300), drawerContent);
        tt.setToX(550);
        tt.setOnFinished(e -> {
            drawerPane.setVisible(false);
            drawerPane.setMouseTransparent(true);
            detailContainer.getChildren().clear();
        });
        tt.play();
    }

    @FXML
    private void handleOverlayClick(MouseEvent event) {
        if (event.getX() < (drawerPane.getWidth() - drawerContent.getWidth())) {
            closeDrawer();
        }
    }

    private void createTableViewStructure() {
        deviceTable = new TableView<>();
        deviceTable.getStyleClass().add("custom-table-view");
        deviceTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        deviceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        rowNumberColumn = new TableColumn<>("序号");
        rowNumberColumn.setMinWidth(50);
        rowNumberColumn.setMaxWidth(50);
        rowNumberColumn.setCellFactory(col -> new TableCell<Device, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setText(null);
                else setText(String.valueOf(getIndex() + 1));
            }
        });

        snCol = new TableColumn<>("序列号");
        snCol.setCellValueFactory(new PropertyValueFactory<>("deviceSn"));
        snCol.setMinWidth(80);

        aliasCol = new TableColumn<>("別名");
        aliasCol.setCellValueFactory(new PropertyValueFactory<>("alias"));
        aliasCol.setMinWidth(50);

        locCol = new TableColumn<>("位置");
        locCol.setCellValueFactory(new PropertyValueFactory<>("location"));
        locCol.setMinWidth(100);

        modelCol = new TableColumn<>("型号");
        modelCol.setCellValueFactory(new PropertyValueFactory<>("model"));
        modelCol.setMinWidth(100);

        ipCol = new TableColumn<>("IP地址");
        ipCol.setCellValueFactory(new PropertyValueFactory<>("ipAddress"));
        ipCol.setMinWidth(120);

        activeCol = new TableColumn<>("状态");
        activeCol.setCellValueFactory(new PropertyValueFactory<>("active"));
        ipCol.setMinWidth(50);
        ipCol.setMinWidth(50);
        activeCol.setCellFactory(column -> new TableCell<Device, Boolean>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item ? "已激活" : "未激活");
                    setStyle(item ? "-fx-text-fill: #2ecc71;" : "-fx-text-fill: #ff3333;");
                }
            }
        });

        deviceTable.getColumns().addAll(rowNumberColumn, snCol, aliasCol, locCol, modelCol, ipCol, activeCol);
    }

    private void setupActionColumn() {
        TableColumn<Device, Void> actionCol = new TableColumn<>("操作");
        actionCol.setCellFactory(col -> new TableCell<Device, Void>() {
            private final Button editButton = new Button("修改");

            {
                editButton.getStyleClass().add("action-btn-detail");
                editButton.setOnAction(event -> openViewInDrawer("/view/DeviceEditFormView.fxml", getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null);
                else {
                    HBox pane = new HBox(editButton);
                    pane.setAlignment(Pos.CENTER);
                    setGraphic(pane);
                }
            }
        });
        deviceTable.getColumns().add(actionCol);
    }

    public void loadDeviceData() {
        // 1. 显示 Loading
        loadingManager.show("正在获取设备数据...");

        DeviceRequest request = new DeviceRequest();
        request.setDeviceSn(searchSn.getText() == null ? "" : searchSn.getText().trim());
        String filterValue = activeFilter.getValue();
        if ("全部".equals(filterValue)) {
            request.setActive(null);
        } else {
            request.setActive("已激活".equals(filterValue));
        }
        statusLabel.setText("正在加载设备数据...");

        // 使用 Task 进行异步处理，防止 UI 线程阻塞
        javafx.concurrent.Task<java.util.List<Device>> loadTask = new javafx.concurrent.Task<java.util.List<Device>>() {
            @Override
            protected java.util.List<Device> call() throws Exception {
                // 调用服务层获取数据
                return deviceService.getDeviceList(request);
            }
        };

        loadTask.setOnSucceeded(e -> {
            // 2. 成功后隐藏
            loadingManager.hide();
            List<Device> result = loadTask.getValue();
            if (result != null) {
                deviceTable.setItems(FXCollections.observableArrayList(loadTask.getValue()));
                statusLabel.setText(String.format("加載完成，共 %d 台设备", loadTask.getValue().size()));
            } else {
                deviceTable.setItems(FXCollections.observableArrayList());
                statusLabel.setText("未获取到数据内容");
            }
        });

        loadTask.setOnFailed(e -> {
            Throwable exception = loadTask.getException();
            if (exception instanceof SocketTimeoutException || exception.getMessage().contains("timeout")) {
                // 3. 超时显示重试按钮，重试逻辑就是再次调用本方法
                loadingManager.showTimeout(() -> loadDeviceData());
            } else {
                loadingManager.hide();
                CustomAlertDialog.showError("错误", "加载失败");
            }
        });

        new Thread(loadTask).start();
    }

    @FXML
    private void handleSearch() {
        loadDeviceData();
    }

    @FXML
    private void handleNewDevice() {
        openViewInDrawer("/view/DeviceEditFormView.fxml", null);
    }

    private void handleEditDevice(Device device) {
        openViewInDrawer("/view/DeviceEditFormView.fxml", device);
    }

    @FXML
    private void handleSynchronize() {
        // 1. 获取选中的设备列表
        ObservableList<Device> selectedItems = deviceTable.getSelectionModel().getSelectedItems();

        if (selectedItems.isEmpty()) {
            CustomAlertDialog.showWarning(null, "请选择至少一台设备进行同步。");
            return;
        }

        // 2. 提取所有选中设备的 SN
        List<String> sns = new ArrayList<>();
        for (Device device : selectedItems) {
            sns.add(device.getDeviceSn());
        }

        statusLabel.setText("正在下发同步指令...");

        // 1. 显示 Loading
        loadingManager.show("正在获取设备数据...");
        // 3. 异步下发指令
        javafx.concurrent.Task<Boolean> syncTask = new javafx.concurrent.Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                return deviceService.syncAttendanceData(sns);
            }
        };

        syncTask.setOnSucceeded(e -> {
            // 2. 成功后隐藏
            loadingManager.hide();
            if (syncTask.getValue()) {
                statusLabel.setText("同步指令下发成功，设备正在处理...");
                CustomAlertDialog.showInfo("指令下发成功", "考勤数据同步任务已启动，请稍后查看。");
            } else {
                statusLabel.setText("同步指令执行失败");
                CustomAlertDialog.showError("执行失败", "服务器未能正确处理同步请求。");
            }
        });

        syncTask.setOnFailed(e -> {
            Throwable exception = syncTask.getException();
            if (exception instanceof SocketTimeoutException || exception.getMessage().contains("timeout")) {
                // 3. 超时显示重试按钮，重试逻辑就是再次调用本方法
                loadingManager.showTimeout(() -> handleSynchronize());
            } else {
                statusLabel.setText("通信异常");
                CustomAlertDialog.showError("网络错误", "无法连接到服务器下发指令。");
            }
        });

        new Thread(syncTask).start();
    }

}