package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.dto.SyncRequest;
import com.ray.atten.desktop.model.AttendanceGroup;
import com.ray.atten.desktop.model.OaEmployee;
import com.ray.atten.desktop.service.AttendanceService;
import com.ray.atten.desktop.service.OaEmployeeService;
import com.ray.atten.desktop.utils.AppConstants;
import com.ray.atten.desktop.utils.CustomAlertDialog;
import com.ray.atten.desktop.utils.LoadingManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SyncGroupController {

    @FXML
    private TableView<OaEmployee> employeeTableView;
    @FXML
    private TableColumn<OaEmployee, Integer> rowNumberColumn;
    @FXML
    private TableColumn<OaEmployee, String> pinColumn, nameColumn, fingerprintColumn, photoBase64Column;
    @FXML
    private TableColumn<OaEmployee, LocalDateTime> entryDateColumn;
    @FXML
    private TableColumn<OaEmployee, Void> actionColumn;

    @FXML
    private ComboBox<AttendanceGroup> groupComboBox;
    @FXML
    private Button confirmButton;
    @FXML
    private VBox rootPane;

    @Autowired
    private OaEmployeeService oaEmployeeService;

    @Autowired
    private AttendanceService attendanceService;

    private ObservableList<OaEmployee> employeesToSync;

    @Autowired
    private LoadingManager loadingManager;

    // 【关键】用于关闭抽屉的回调逻辑
    private Runnable onCloseRequest;

    public void setOnCloseRequest(Runnable onCloseRequest) {
        this.onCloseRequest = onCloseRequest;
    }

    @FXML
    public void initialize() {
        confirmButton.setDisable(true);
        setupDateTimeColumnFormatting(entryDateColumn);
        // 监听考勤组选择逻辑
        groupComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            boolean disable = (newV == null || newV.getDeviceSns() == null || newV.getDeviceSns().isEmpty());
            confirmButton.setDisable(disable);
        });

        // 考勤组 ComboBox 显示格式
        groupComboBox.setCellFactory(lv -> new ListCell<AttendanceGroup>() {
            @Override
            protected void updateItem(AttendanceGroup item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item.getGroupName());
            }
        });
        groupComboBox.setButtonCell(groupComboBox.getCellFactory().call(null));

        setupTableColumns();
        setupActionColumn();
    }

    private void setupTableColumns() {
        pinColumn.setCellValueFactory(new PropertyValueFactory<>("pin"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        entryDateColumn.setCellValueFactory(new PropertyValueFactory<>("entryDate"));

        setupExistenceColumn(fingerprintColumn, OaEmployee::getFingerprint);
        setupExistenceColumn(photoBase64Column, OaEmployee::getPhotoBase64);

        rowNumberColumn.setCellFactory(col -> new TableCell<OaEmployee, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                } else {
                    setText(String.valueOf(getIndex() + 1));
                    setAlignment(Pos.CENTER);
                }
            }
        });
    }

    private void setupExistenceColumn(TableColumn<OaEmployee, String> column, Function<OaEmployee, String> valueGetter) {
        column.setCellFactory(col -> new TableCell<OaEmployee, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                } else {
                    OaEmployee employee = getTableView().getItems().get(getIndex());
                    String value = valueGetter.apply(employee);
                    boolean exists = value != null && !value.trim().isEmpty();
                    setText(exists ? "有" : "无");
                    setStyle(exists ? "-fx-text-fill: #27ae60; -fx-font-weight: bold;" : "-fx-text-fill: #7f8c8d;");
                    setAlignment(Pos.CENTER);
                }
            }
        });
    }

    private void setupActionColumn() {
        actionColumn.setCellFactory(col -> new TableCell<OaEmployee, Void>() {
            private final Button deleteButton = new Button("移除");

            {
                deleteButton.getStyleClass().add("action-btn-delete"); // 建议在CSS中统一定义
                deleteButton.setOnAction(event -> handleDeleteEmployee(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(deleteButton);
                    setAlignment(Pos.CENTER);
                }
            }
        });
    }

    private void handleDeleteEmployee(OaEmployee employee) {
        boolean isOk = CustomAlertDialog.showConfirmation("", "确定要从列表中移除员工 " + employee.getName() + " 吗？");
        if (isOk) {
            employeesToSync.remove(employee);
        }
    }

    /**
     * 【核心入口】由 EmployeeListController 调用
     */
    public void setEmployeesToSync(List<OaEmployee> employees) {
        this.employeesToSync = FXCollections.observableArrayList(employees);
        employeeTableView.setItems(this.employeesToSync);
        loadAttendanceGroups();
    }

    private void loadAttendanceGroups() {
        Task<List<AttendanceGroup>> task = new Task<List<AttendanceGroup>>() {
            @Override
            protected List<AttendanceGroup> call() throws Exception {
                return oaEmployeeService.getAttendanceGroups();
            }
        };
        task.setOnSucceeded(e -> {
            groupComboBox.getItems().setAll(task.getValue());
        });
        task.setOnFailed(e -> {
            CustomAlertDialog.showError("", "加载考勤组失败: " + task.getException().getMessage());
        });
        new Thread(task).start();
    }

    @FXML
    private void handleConfirmSync() {
        AttendanceGroup selectedGroup = groupComboBox.getValue();
        if (selectedGroup == null) return;

        final String deviceSns = selectedGroup.getDeviceSnsString();
        if (deviceSns.isEmpty()) {
            CustomAlertDialog.showWarning("", "所选考勤组未绑定设备。");
            return;
        }

        List<SyncRequest> syncRequests = employeesToSync.stream()
                .map(employee -> {
                    SyncRequest request = new SyncRequest();
                    request.setPin(employee.getPin());
                    request.setName(employee.getName());
                    request.setFingerprint(employee.getFingerprint());
                    request.setFingerSize(employee.getFingerSize());
                    request.setPhotoBase64(employee.getPhotoBase64());
                    request.setPhotoSize(employee.getPhotoSize());
                    request.setDeviceSn(deviceSns);
                    return request;
                })
                .collect(Collectors.toList());

        performSynchronization(syncRequests);
    }

    private void performSynchronization(List<SyncRequest> requests) {
        loadingManager.show("正在执行......");
        confirmButton.setDisable(true); // 防止重复点击
        Task<Void> syncTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                oaEmployeeService.syncEmployeesToGroup(requests);
                return null;
            }
        };

        syncTask.setOnSucceeded(e -> {
            loadingManager.hide();
            Platform.runLater(() -> {
                // 1. 提示成功
                CustomAlertDialog.showInfo("", "已成功发送 " + requests.size() + " 位员工同步请求。");
                // 2. 调用抽屉关闭回调
                if (onCloseRequest != null) {
                    onCloseRequest.run();
                }
            });
        });

        syncTask.setOnFailed(e -> {
            confirmButton.setDisable(false);
            Throwable exception = syncTask.getException();
            if (exception instanceof SocketTimeoutException || exception.getMessage().contains("timeout")) {
                // 3. 超时显示重试按钮，重试逻辑就是再次调用本方法
                loadingManager.showTimeout(() -> performSynchronization(requests));
            } else {
                loadingManager.hide();
                CustomAlertDialog.showError("", "同步失败: " + syncTask.getException().getMessage());
            }
        });

        new Thread(syncTask).start();
    }

    private void setupDateTimeColumnFormatting(TableColumn<OaEmployee, LocalDateTime> col) {
        col.setCellFactory(c -> new TableCell<OaEmployee, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime it, boolean em) {
                super.updateItem(it, em);
                setText((em || it == null) ? null : AppConstants.dateTimeFormatter(it, AppConstants.YYYY_MM_DD));
            }
        });
    }
}