package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.dto.DeviceRequest;
import com.ray.atten.desktop.model.Device;
import com.ray.atten.desktop.service.DeviceService;
import com.ray.atten.desktop.utils.CustomAlertDialog;
import com.ray.atten.desktop.utils.LoadingManager;
import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
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
import java.util.stream.Collectors;

@Component
public class DeviceManagementController {

    @Autowired
    private ApplicationContext springContext;
    @Autowired
    private DeviceService deviceService;
    @Autowired
    private LoadingManager loadingManager;

    @FXML private AnchorPane drawerPane;
    @FXML private VBox drawerContent;
    @FXML private StackPane detailContainer;
    @FXML private AnchorPane tableContainer;

    @FXML private TextField searchSn;
    @FXML private ComboBox<String> activeFilter;
    @FXML private Label statusLabel;

    // --- TableView 相關 ---
    private TableView<Device> deviceTable;
    private TableColumn<Device, Boolean> selectColumn; // 替换原 rowNumberColumn
    private TableColumn<Device, String> snCol, aliasCol, locCol, modelCol, ipCol;
    private TableColumn<Device, Boolean> activeCol;
    private CheckBox selectAllCheckBox;

    @FXML
    public void initialize() {
        createTableViewStructure();
        setupActionColumn();

        activeFilter.setItems(FXCollections.observableArrayList("全部", "已激活", "未激活"));
        activeFilter.setValue("全部");

        tableContainer.getChildren().add(deviceTable);
        AnchorPane.setTopAnchor(deviceTable, 0.0);
        AnchorPane.setBottomAnchor(deviceTable, 0.0);
        AnchorPane.setLeftAnchor(deviceTable, 0.0);
        AnchorPane.setRightAnchor(deviceTable, 0.0);

        loadDeviceData();
    }

    private void createTableViewStructure() {
        deviceTable = new TableView<>();
        deviceTable.getStyleClass().add("custom-table-view");
        deviceTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        deviceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        deviceTable.setEditable(true); // 开启编辑模式以支持 CheckBox 点击

        // 1. 创建全选复选框列
        selectAllCheckBox = new CheckBox();
        selectAllCheckBox.setOnAction(e -> {
            boolean selected = selectAllCheckBox.isSelected();
            deviceTable.getItems().forEach(d -> d.setSelected(selected));
        });

        selectColumn = new TableColumn<>();
        selectColumn.setGraphic(selectAllCheckBox);
        selectColumn.setMinWidth(40);
        selectColumn.setMaxWidth(40);
        selectColumn.setSortable(false);
        // 绑定模型中的 selectedProperty
        selectColumn.setCellValueFactory(data -> data.getValue().selectedProperty());
        selectColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectColumn));

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
        activeCol.setCellFactory(column -> new TableCell<Device, Boolean>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    setText(item ? "已激活" : "未激活");
                    setStyle(item ? "-fx-text-fill: #2ecc71;" : "-fx-text-fill: #ff3333;");
                }
            }
        });

        deviceTable.getColumns().addAll(selectColumn, snCol, aliasCol, locCol, modelCol, ipCol, activeCol);
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
        loadingManager.show("正在获取设备数据...");
        DeviceRequest request = new DeviceRequest();
        request.setDeviceSn(searchSn.getText() == null ? "" : searchSn.getText().trim());
        String filterValue = activeFilter.getValue();
        request.setActive("全部".equals(filterValue) ? null : "已激活".equals(filterValue));

        Task<List<Device>> loadTask = new Task<List<Device>>() {
            @Override
            protected List<Device> call() throws Exception {
                return deviceService.getDeviceList(request);
            }
        };

        loadTask.setOnSucceeded(e -> {
            loadingManager.hide();
            List<Device> result = loadTask.getValue();
            // 加载新数据时重置全选状态
            selectAllCheckBox.setSelected(false);
            if (result != null) {
                deviceTable.setItems(FXCollections.observableArrayList(result));
                statusLabel.setText(String.format("加載完成，共 %d 台设备", result.size()));
            } else {
                deviceTable.setItems(FXCollections.observableArrayList());
                statusLabel.setText("未获取到数据内容");
            }
        });

        loadTask.setOnFailed(e -> {
            Throwable exception = loadTask.getException();
            if (exception instanceof SocketTimeoutException || (exception.getMessage() != null && exception.getMessage().contains("timeout"))) {
                loadingManager.showTimeout(this::loadDeviceData);
            } else {
                loadingManager.hide();
                CustomAlertDialog.showError("错误", "加载失败");
            }
        });

        new Thread(loadTask).start();
    }

    @FXML
    private void handleSynchronize() {
        // 修改为通过 filter 获取勾选的设备
        List<Device> selectedItems = deviceTable.getItems().stream()
                .filter(Device::isSelected)
                .collect(Collectors.toList());

        if (selectedItems.isEmpty()) {
            CustomAlertDialog.showWarning(null, "请勾选至少一台设备进行同步。");
            return;
        }

        List<String> sns = selectedItems.stream()
                .map(Device::getDeviceSn)
                .collect(Collectors.toList());

        statusLabel.setText("正在下发同步指令...");
        loadingManager.show("正在提交同步请求...");

        Task<Boolean> syncTask = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                return deviceService.syncAttendanceData(sns);
            }
        };

        syncTask.setOnSucceeded(e -> {
            loadingManager.hide();
            if (syncTask.getValue()) {
                statusLabel.setText("同步指令下发成功");
                CustomAlertDialog.showInfo("指令下发成功", "考勤数据同步任务已启动。");
            } else {
                statusLabel.setText("同步指令执行失败");
                CustomAlertDialog.showError("执行失败", "服务器未能正确处理同步请求。");
            }
        });

        syncTask.setOnFailed(e -> {
            loadingManager.hide();
            statusLabel.setText("通信异常");
            CustomAlertDialog.showError("网络错误", "无法连接到服务器。");
        });

        new Thread(syncTask).start();
    }

    // --- 抽屉逻辑 (保持不变) ---
    private void openViewInDrawer(String fxmlPath, Device data) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/DeviceEditFormView.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Node node = loader.load();
            Object controller = loader.getController();
            if (controller instanceof DeviceEditController) {
                ((DeviceEditController) controller).setParentController(this);
                ((DeviceEditController) controller).initData(data);
                ((DeviceEditController) controller).setOnCloseRequest(this::closeDrawer);
            }
            detailContainer.getChildren().setAll(node);
            showDrawer();
        } catch (IOException e) {
            e.printStackTrace();
            CustomAlertDialog.showError("", "无法加载界面");
        }
    }

    private void showDrawer() {
        if (drawerPane == null) return;
        drawerPane.setVisible(true);
        drawerPane.setMouseTransparent(false);
        TranslateTransition tt = new TranslateTransition(Duration.millis(500), drawerContent);
        tt.setFromX(550); tt.setToX(0);
        tt.play();
    }

    @FXML public void closeDrawer() {
        TranslateTransition tt = new TranslateTransition(Duration.millis(300), drawerContent);
        tt.setToX(550);
        tt.setOnFinished(e -> {
            drawerPane.setVisible(false);
            drawerPane.setMouseTransparent(true);
            detailContainer.getChildren().clear();
        });
        tt.play();
    }

    @FXML private void handleOverlayClick(MouseEvent event) {
        if (event.getX() < (drawerPane.getWidth() - drawerContent.getWidth())) closeDrawer();
    }

    @FXML private void handleSearch() { loadDeviceData(); }
    @FXML private void handleNewDevice() { openViewInDrawer("/view/DeviceEditFormView.fxml", null); }
}