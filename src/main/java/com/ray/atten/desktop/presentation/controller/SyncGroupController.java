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
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.controlsfx.control.CheckComboBox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SyncGroupController {

    @FXML
    private TableView<OaEmployee> employeeTableView;
    @FXML
    private TableColumn<OaEmployee, Integer> rowNumberColumn;
    // 注意：这里的 TableColumn 类型改为 <OaEmployee, OaEmployee>
    // 或者干脆不显式绑定类型，因为我们要通过 cellFactory 处理整行对象
    @FXML
    private TableColumn<OaEmployee, String> pinColumn, nameColumn, fingerprintColumn, photoBase64Column;
    @FXML
    private TableColumn<OaEmployee, LocalDateTime> entryDateColumn;
    @FXML
    private TableColumn<OaEmployee, Void> actionColumn;

    @FXML
    private StackPane groupSelectorContainer;
    @FXML
    private Button confirmButton;
    @FXML
    private VBox rootPane;

    @Autowired
    private OaEmployeeService oaEmployeeService;

    private ObservableList<OaEmployee> employeesToSync;

    @Autowired
    private LoadingManager loadingManager;

    private CheckComboBox<AttendanceGroup> groupCheckComboBox;
    private ObservableList<AttendanceGroup> allGroups = FXCollections.observableArrayList();

    private Runnable onCloseRequest;

    public void setOnCloseRequest(Runnable onCloseRequest) {
        this.onCloseRequest = onCloseRequest;
    }

    @FXML
    public void initialize() {
        confirmButton.setDisable(true);
        setupDateTimeColumnFormatting(entryDateColumn);

        initGroupCheckComboBox();
        setupTableColumns();
        setupActionColumn();
    }

    private void initGroupCheckComboBox() {
        groupCheckComboBox = new CheckComboBox<>(allGroups);
        groupCheckComboBox.setPrefWidth(250.0);
        groupCheckComboBox.setTitle("请选择目标设备组");

        groupCheckComboBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(AttendanceGroup group) {
                return group == null ? "" : group.getGroupName();
            }
            @Override
            public AttendanceGroup fromString(String string) { return null; }
        });

        groupCheckComboBox.getCheckModel().getCheckedItems().addListener((ListChangeListener<AttendanceGroup>) c -> {
            ObservableList<AttendanceGroup> selectedItems = groupCheckComboBox.getCheckModel().getCheckedItems();
            if (selectedItems.isEmpty()) {
                groupCheckComboBox.setTitle("请选择目标设备组");
            } else {
                String combinedNames = selectedItems.stream()
                        .map(AttendanceGroup::getGroupName)
                        .collect(Collectors.joining(", "));
                if (selectedItems.size() > 2) {
                    groupCheckComboBox.setTitle("已选择 " + selectedItems.size() + " 个设备组");
                } else {
                    groupCheckComboBox.setTitle(combinedNames);
                }
            }
            updateConfirmButtonState();
        });

        groupSelectorContainer.getChildren().clear();
        groupSelectorContainer.getChildren().add(groupCheckComboBox);
    }

    private void updateConfirmButtonState() {
        ObservableList<AttendanceGroup> selectedGroups = groupCheckComboBox.getCheckModel().getCheckedItems();
        boolean hasSelectedGroups = !selectedGroups.isEmpty();
        boolean hasEmployees = employeesToSync != null && !employeesToSync.isEmpty();
        confirmButton.setDisable(!hasSelectedGroups || !hasEmployees);
    }

    private void setupTableColumns() {
        // 保持存在的字段绑定
        pinColumn.setCellValueFactory(new PropertyValueFactory<>("pin"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        entryDateColumn.setCellValueFactory(new PropertyValueFactory<>("entryDate"));

        // 2. 【核心修复】强制清空可能存在的旧 Factory
        // 这样做可以覆盖 FXML 中可能隐藏的设置，确保安全
        fingerprintColumn.setCellValueFactory(null);
        photoBase64Column.setCellValueFactory(null);

        // 3. 手动定义“有/无”的渲染逻辑
        setupExistenceColumn(fingerprintColumn, emp -> {
            // 这里的逻辑不依赖反射，而是直接调用 List 接口
            boolean hasFinger = emp.getSyncList() != null &&
                    emp.getSyncList().stream().anyMatch(s -> "finger".equals(s.getType()));
            return hasFinger ? "EXISTS" : null;
        });

        setupExistenceColumn(photoBase64Column, OaEmployee::getPhotoBase64);

        rowNumberColumn.setCellFactory(col -> new TableCell<>() {
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

    private void setupExistenceColumn(TableColumn<OaEmployee, String> column, java.util.function.Function<OaEmployee, String> valueGetter) {
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                // 必须检查当前行索引是否有效
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setText(null);
                    setStyle("");
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
        actionColumn.setCellFactory(col -> new TableCell<>() {
            private final Button deleteButton = new Button("移除");
            {
                deleteButton.getStyleClass().add("action-btn-delete");
                deleteButton.setOnAction(event -> handleDeleteEmployee(getTableView().getItems().get(getIndex())));
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteButton);
                if (!empty) setAlignment(Pos.CENTER);
            }
        });
    }

    private void handleDeleteEmployee(OaEmployee employee) {
        if (CustomAlertDialog.showConfirmation("", "确定要从列表中移除员工 " + employee.getName() + " 吗？")) {
            employeesToSync.remove(employee);
            updateConfirmButtonState();
        }
    }

    public void setEmployeesToSync(List<OaEmployee> employees) {
        this.employeesToSync = FXCollections.observableArrayList(employees);
        employeeTableView.setItems(this.employeesToSync);
        loadAttendanceGroups();
    }

    private void loadAttendanceGroups() {
        Task<List<AttendanceGroup>> task = new Task<>() {
            @Override
            protected List<AttendanceGroup> call() throws IOException {
                return oaEmployeeService.getAttendanceGroups();
            }
        };
        task.setOnSucceeded(e -> allGroups.setAll(task.getValue()));
        new Thread(task).start();
    }

    @FXML
    private void handleConfirmSync() {
        ObservableList<AttendanceGroup> selectedGroups = groupCheckComboBox.getCheckModel().getCheckedItems();
        if (selectedGroups.isEmpty()) return;

        String combinedSns = selectedGroups.stream()
                .map(AttendanceGroup::getDeviceSnsString)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(","));

        if (combinedSns.isEmpty()) {
            CustomAlertDialog.showWarning("", "所选考勤组未绑定设备。");
            return;
        }

        List<SyncRequest> syncRequests = employeesToSync.stream()
                .map(employee -> {
                    SyncRequest request = new SyncRequest();
                    request.setPin(employee.getPin());
                    request.setName(employee.getName());
                    request.setFingerFidList(employee.getSyncList()); // 发送完整的生物识别列表
                    request.setDeviceSn(combinedSns);
                    return request;
                })
                .collect(Collectors.toList());

        performSynchronization(syncRequests);
    }

    private void performSynchronization(List<SyncRequest> requests) {
        loadingManager.show("正在执行同步......");
        confirmButton.setDisable(true);
        Task<Void> syncTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                oaEmployeeService.syncEmployeesToGroup(requests);
                return null;
            }
        };

        syncTask.setOnSucceeded(e -> {
            loadingManager.hide();
            Platform.runLater(() -> {
                CustomAlertDialog.showInfo("", "已成功发送 " + requests.size() + " 位员工同步请求。");
                if (onCloseRequest != null) onCloseRequest.run();
            });
        });

        syncTask.setOnFailed(e -> {
            confirmButton.setDisable(false);
            Throwable exception = syncTask.getException();
            if (exception instanceof SocketTimeoutException || (exception.getMessage() != null && exception.getMessage().contains("timeout"))) {
                loadingManager.showTimeout(() -> performSynchronization(requests));
            } else {
                loadingManager.hide();
                CustomAlertDialog.showError("", "同步失败");
            }
        });

        new Thread(syncTask).start();
    }

    private void setupDateTimeColumnFormatting(TableColumn<OaEmployee, LocalDateTime> col) {
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime it, boolean em) {
                super.updateItem(it, em);
                setText((em || it == null) ? null : AppConstants.dateTimeFormatter(it, AppConstants.YYYY_MM_DD));
            }
        });
    }
}