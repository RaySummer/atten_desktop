package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.dto.SyncRequest;
import com.ray.atten.desktop.model.AttendanceGroup;
import com.ray.atten.desktop.model.OaEmployee;
import com.ray.atten.desktop.service.OaEmployeeService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SyncGroupController {

    // 【修改點 2】：替換為 TableView 和 TableColumn
    @FXML
    private TableView<OaEmployee> employeeTableView;
    @FXML
    private TableColumn<OaEmployee, Integer> rowNumberColumn;
    @FXML
    private TableColumn<OaEmployee, String> pinColumn;
    @FXML
    private TableColumn<OaEmployee, String> nameColumn;
    @FXML
    private TableColumn<OaEmployee, LocalDateTime> entryDateColumn;
    @FXML
    private TableColumn<OaEmployee, String> fingerprintColumn;
    @FXML
    private TableColumn<OaEmployee, String> photoBase64Column;
    @FXML
    private TableColumn<OaEmployee, Void> actionColumn; // Void 表示這列不綁定數據

    @FXML
    private ComboBox<AttendanceGroup> groupComboBox;
    @FXML
    private Button confirmButton;
    @FXML
    private VBox rootPane; // 用於展示進度或狀態

    // 數據和服務實例
    private ObservableList<OaEmployee> employeesToSync;

    @Autowired
    private OaEmployeeService oaEmployeeService;

    // --- 初始化和數據設置 ---

    @FXML
    public void initialize() {
        // 確保按鈕初始禁用 (防止未選擇考勤組)
        confirmButton.setDisable(true);

        // 監聽 ComboBox 選擇變化
        groupComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            // 啟用/禁用按鈕邏輯：只有在選擇了非空且設備列表非空的考勤組時才啟用
            boolean disable = (newV == null || newV.getDeviceSns() == null || newV.getDeviceSns().isEmpty());
            confirmButton.setDisable(disable);
        });

        // 設置 ComboBox 的顯示格式 (顯示 groupName)
        groupComboBox.setCellFactory(lv -> new ListCell<AttendanceGroup>() {
            @Override
            protected void updateItem(AttendanceGroup item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item.getGroupName());
            }
        });
        // 設置選中後 ComboBox 按鈕上顯示的格式
        groupComboBox.setButtonCell(groupComboBox.getCellFactory().call(null));

        setupTableColumn();

        // 3. 設置操作列 (Delete Button/Icon)
        setupActionColumn();
    }

    private void setupTableColumn() {

        // 1. 綁定數據列
        pinColumn.setCellValueFactory(new PropertyValueFactory<>("pin"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        entryDateColumn.setCellValueFactory(new PropertyValueFactory<>("entryDate"));

        pinColumn.setMinWidth(80);
        nameColumn.setMinWidth(100);
        entryDateColumn.setMinWidth(150);
        actionColumn.setMinWidth(50); // 增加寬度以容納兩個按鈕
        actionColumn.setResizable(false);

        // 绑定指纹数据列 (fingerprint)
        setupExistenceColumn(fingerprintColumn, OaEmployee::getFingerprint);

        // 绑定照片数据列 (photoBase64)
        setupExistenceColumn(photoBase64Column, OaEmployee::getPhotoBase64);

        // 2. 設置序號列 (Row Number)
        rowNumberColumn.setCellFactory(col -> new TableCell<OaEmployee, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0) {
                    setText(null);
                } else {
                    setText(String.valueOf(getIndex() + 1));
                    setAlignment(Pos.CENTER);
                }
            }
        });

    }

    /**
     * 通用的设置方法，用于判断字段是否存在并显示“有”或“无”。
     *
     * @param column 要设置的 TableColumn
     * @param valueGetter 从 OaEmployee 对象中获取待检查字段的函数
     */
    private void setupExistenceColumn(TableColumn<OaEmployee, String> column, Function<OaEmployee, String> valueGetter) {
        column.setCellFactory(col -> new TableCell<OaEmployee, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // 获取当前行的 OaEmployee 对象
                    OaEmployee employee = getTableView().getItems().get(getIndex());
                    String value = valueGetter.apply(employee);

                    // 判断字段是否有数据
                    boolean exists = value != null && !value.trim().isEmpty();

                    setText(exists ? "有" : "無");

                    // 可选：添加样式区分
                    if (exists) {
                        setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: gray;");
                    }
                    setAlignment(Pos.CENTER);
                }
            }
        });
        column.setMinWidth(60);
        column.setResizable(false);
        column.setStyle("-fx-alignment: CENTER;"); // 确保列头和内容居中
    }

    /**
     * 設置待同步的員工列表並初始化 UI 數據。
     */
    private void setupActionColumn() {
        actionColumn.setCellFactory(col -> new TableCell<OaEmployee, Void>() {
            private final Button deleteButton = new Button("刪除");

            {
                // 設置按鈕的點擊事件
                deleteButton.setOnAction(event -> {
                    OaEmployee employee = getTableView().getItems().get(getIndex());
                    handleDeleteEmployee(employee);
                });
                // 設置按鈕樣式 (可替換為圖標)
                deleteButton.getStyleClass().add("warning");
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

    /**
     * 處理刪除員工的邏輯，將其從待同步列表中移除。
     */
    private void handleDeleteEmployee(OaEmployee employee) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "確定要從列表中移除員工 " + employee.getName() + " 嗎？");
        Optional<ButtonType> result = alert.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            // 從 ObservableList 中移除，TableView 會自動更新
            employeesToSync.remove(employee);
            // 序號列會自動重新計算

            System.out.println("員工 " + employee.getName() + " 已被移除。");
        }
    }

    /**
     * 【核心方法】設置待同步的員工列表並初始化 UI 數據。
     *
     * @param employees 傳入的 List<OaEmployee>
     */
    public void setEmployeesToSync(List<OaEmployee> employees) {
        // 【關鍵步驟 1】：將傳入的 List<OaEmployee> 轉換為 ObservableList
        this.employeesToSync = FXCollections.observableArrayList(employees);

        // 【關鍵步驟 2】：將 ObservableList 設置給 TableView 作為數據源
        employeeTableView.setItems(this.employeesToSync);

        // 加載考勤組數據
        loadAttendanceGroups();
    }
//    public void setEmployeesToSync(List<OaEmployee> employees) {
//        this.employeesToSync = employees;
//
//        // 顯示員工 PIN 和姓名
//        employeeListView.getItems().addAll(employees.stream()
//                .map(e -> e.getPin() + " - " + e.getName() + "  -  " + e.getEntryDate())
//                .collect(Collectors.toList()));
//
//        // 加載考勤組數據
//        loadAttendanceGroups();
//    }

    // --- 數據加載邏輯 ---

    /**
     * 異步加載考勤組列表。
     */
    private void loadAttendanceGroups() {
        try {

            Task<List<AttendanceGroup>> task = new Task<List<AttendanceGroup>>() {
                @Override
                protected List<AttendanceGroup> call() throws Exception {
                    // 調用修改後的服務方法
                    return oaEmployeeService.getAttendanceGroups();
                }
            };

            task.setOnSucceeded(e -> {
                groupComboBox.getItems().clear();
                groupComboBox.getItems().addAll(task.getValue());
            });

            task.setOnFailed(e -> {
                // 顯示加載錯誤
                Alert alert = new Alert(Alert.AlertType.ERROR, "加載考勤組失敗: " + task.getException().getMessage());
                alert.showAndWait();
            });

            // 使用新線程執行任務
            new Thread(task).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 點擊「確定同步」按鈕時觸發的動作：構造請求並執行同步。
     */
    @FXML
    private void handleConfirmSync() {
        AttendanceGroup selectedGroup = groupComboBox.getValue();

        if (selectedGroup == null) return;

        // 1. 獲取考勤組綁定的設備序列號
        final String deviceSns = selectedGroup.getDeviceSnsString();

        if (deviceSns.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "所選考勤組未綁定任何設備，無法同步。");
            alert.showAndWait();
            return;
        }

        // 2. 構造請求列表 (List<SyncRequest>)
        List<SyncRequest> syncRequests = employeesToSync.stream()
                .map(employee -> {
                    SyncRequest request = new SyncRequest();

                    // 賦值基本信息
                    request.setPin(employee.getPin());
                    request.setName(employee.getName());

                    // 賦值生物識別數據 (假設 OaEmployee 包含這些字段)
                    // 警告: 如果這些字段為 null 且後端需要，這裡需要額外的查詢邏輯
                    request.setFingerprint(employee.getFingerprint());
                    request.setFingerSize(employee.getFingerSize());
                    request.setPhotoBase64(employee.getPhotoBase64());
                    request.setPhotoSize(employee.getPhotoSize());

                    // 賦值目標設備 SN
                    request.setDeviceSn(deviceSns);

                    return request;
                })
                .collect(Collectors.toList());

        // 3. 執行同步任務
        performSynchronization(syncRequests);

        // 4. 關閉彈窗
        ((Stage) rootPane.getScene().getWindow()).close();
    }

    /**
     * 異步執行批量同步到考勤機的 API 調用。
     *
     * @param requests 包含所有員工同步數據的列表
     */
    private void performSynchronization(List<SyncRequest> requests) {
        Task<Void> syncTask = new Task<Void>() {

            @Override
            protected Void call() throws Exception {
                // 這裡將整個 List<SyncRequest> 作為 JSON 數組發送
                oaEmployeeService.syncEmployeesToGroup(requests);

                // Task<Void> 必須返回 null
                return null;
            }
        };

        syncTask.setOnSucceeded(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "成功發送 " + requests.size() + " 位員工同步請求到考勤組: " + groupComboBox.getValue().getGroupName());
            alert.show();
        });

        syncTask.setOnFailed(e -> {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "同步請求失敗: " + syncTask.getException().getMessage());
            alert.show();
            // 打印堆棧信息以便於調試
            syncTask.getException().printStackTrace();
        });

        // 啟動新線程執行任務
        new Thread(syncTask).start();
    }
}
