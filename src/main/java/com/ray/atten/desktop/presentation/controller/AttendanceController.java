package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.dto.AttendanceLogRequest;
import com.ray.atten.desktop.dto.PageResponse;
import com.ray.atten.desktop.model.AttendanceLog;
import com.ray.atten.desktop.service.AttendanceService;
import com.ray.atten.desktop.utils.AppConstants;
import com.ray.atten.desktop.utils.CustomAlertDialog;
import com.ray.atten.desktop.utils.LoadingManager;
import javafx.animation.TranslateTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;

@Component
public class AttendanceController {

    @Autowired
    private AttendanceService attendanceService;
    @Autowired
    private ApplicationContext springContext;

    // --- 側邊抽屉控件 ---
    @FXML
    private AnchorPane drawerPane;
    @FXML
    private VBox drawerContent;
    @FXML
    private StackPane detailContainer;

    // --- FXML 控件 ---
    @FXML
    private TextField keywordField;
    @FXML
    private DatePicker startDatePicker, endDatePicker;
    @FXML
    private Label statusLabel;
    @FXML
    private Pagination pagination;
    @FXML
    private ComboBox<Integer> pageSizeComboBox;
    @Autowired
    private LoadingManager loadingManager;

    // --- 动态 TableView ---
    private TableView<AttendanceLog> attendanceTable;
    private TableColumn<AttendanceLog, Integer> rowNumberColumn;
    private TableColumn<AttendanceLog, String> pinColumn, nameColumn, deviceSnColumn, verifyTypeColumn;
    private TableColumn<AttendanceLog, LocalDateTime> verifyTimeColumn;
//    private TableColumn<AttendanceLog, Void> actionColumn;

    // --- 分頁與配置 ---
    private final ObservableList<Integer> pageSizeOptions = FXCollections.observableArrayList(10, 20, 50, 100);
    private int currentPageSize = AppConstants.DEFAULT_PAGE_SIZE;
    private long totalRecords = 0;

    @FXML
    public void initialize() {
        createTableViewStructure();
//        setupActionColumn();
        setupDateTimeColumnFormatting();
        setupVerifyTypeColumnFormatting();

        // 优化日期控件交互
        setupDatePickerInteractions(startDatePicker);
        setupDatePickerInteractions(endDatePicker);
        setupPaginationAndControls();
        updatePaginationMetadata();
        statusLabel.setText("考勤记录列表已就绪。");
    }

    private void createTableViewStructure() {
        attendanceTable = new TableView<>();
        attendanceTable.getStyleClass().add("custom-table-view");
        attendanceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        rowNumberColumn = new TableColumn<>("序号");
        pinColumn = new TableColumn<>("工号");
        nameColumn = new TableColumn<>("姓名");
        deviceSnColumn = new TableColumn<>("设备序列号");
        verifyTypeColumn = new TableColumn<>("打卡方式");
        verifyTimeColumn = new TableColumn<>("打卡时间");
//        actionColumn = new TableColumn<>("操作");

        rowNumberColumn.setMinWidth(50);
        rowNumberColumn.setMaxWidth(50);
//        actionColumn.setMinWidth(120);

        rowNumberColumn.setCellValueFactory(null); // 由 CellFactory 处理
        pinColumn.setCellValueFactory(new PropertyValueFactory<>("userPin"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("userName"));
        deviceSnColumn.setCellValueFactory(new PropertyValueFactory<>("deviceSn"));
        verifyTypeColumn.setCellValueFactory(new PropertyValueFactory<>("verifyType"));
        verifyTimeColumn.setCellValueFactory(new PropertyValueFactory<>("verifyTime"));

        rowNumberColumn.setCellFactory(col -> new TableCell<AttendanceLog, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setText(null);
                else setText(String.valueOf((pagination.getCurrentPageIndex() * currentPageSize) + getIndex() + 1));
            }
        });

        attendanceTable.getColumns().addAll(rowNumberColumn, pinColumn, nameColumn, deviceSnColumn, verifyTypeColumn, verifyTimeColumn);
    }

    private void setupPaginationAndControls() {
        pageSizeComboBox.setValue(currentPageSize);
        pageSizeComboBox.setItems(pageSizeOptions);
        pageSizeComboBox.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                currentPageSize = newVal;
                updatePaginationMetadata();
            }
        });
        pagination.setPageFactory(this::createPage);
    }

    private Node createPage(int pageIndex) {
        loadAttendanceData(pageIndex);
        AnchorPane ap = new AnchorPane(attendanceTable);
        AnchorPane.setTopAnchor(attendanceTable, 0.0);
        AnchorPane.setBottomAnchor(attendanceTable, 0.0);
        AnchorPane.setLeftAnchor(attendanceTable, 0.0);
        AnchorPane.setRightAnchor(attendanceTable, 0.0);
        return ap;
    }

    private void updatePaginationMetadata() {
        loadingManager.show("正在加载中.....");
        AttendanceLogRequest request = buildRequest();
        request.setPageNum(1);
        request.setPageSize(currentPageSize);

        Task<PageResponse<AttendanceLog>> metadataTask = new Task<PageResponse<AttendanceLog>>() {
            @Override
            protected PageResponse<AttendanceLog> call() throws Exception {
                return attendanceService.getAttendanceLogs(request);
            }
        };
        metadataTask.setOnSucceeded(e -> {
            loadingManager.hide();
            PageResponse<AttendanceLog> response = metadataTask.getValue();
            if (response != null) {
                totalRecords = response.getTotalElements();
                pagination.setPageCount(Math.max(1, (int) Math.ceil((double) totalRecords / currentPageSize)));
                loadAttendanceData(pagination.getCurrentPageIndex());
            }
        });
        metadataTask.setOnFailed(e -> {
            Throwable exception = metadataTask.getException();
            if (exception instanceof SocketTimeoutException || exception.getMessage().contains("timeout")) {
                // 3. 超时显示重试按钮，重试逻辑就是再次调用本方法
                loadingManager.showTimeout(() -> updatePaginationMetadata());
            } else {
                loadingManager.hide();
                CustomAlertDialog.showError("错误", "加载失败");
            }
        });
        new Thread(metadataTask).start();
    }

    private void loadAttendanceData(int pageIndex) {
        loadingManager.show("正在加载中.....");
        AttendanceLogRequest request = buildRequest();
        request.setPageNum(pageIndex + 1);
        request.setPageSize(currentPageSize);
        request.setSortBy("verifyTime");
        request.setSortOrder("DESC");

        Task<PageResponse<AttendanceLog>> loadTask = new Task<PageResponse<AttendanceLog>>() {
            @Override
            protected PageResponse<AttendanceLog> call() throws Exception {
                return attendanceService.getAttendanceLogs(request);
            }
        };
        loadTask.setOnSucceeded(e -> {
            loadingManager.hide();
            PageResponse<AttendanceLog> res = loadTask.getValue();
            if (res != null) {
                attendanceTable.setItems(FXCollections.observableArrayList(res.getContent()));
                statusLabel.setText(String.format("页面 %d/%d 加载完成。总记录: %d", pageIndex + 1, pagination.getPageCount(), res.getTotalElements()));
            }
        });
        loadTask.setOnFailed(e -> {
            Throwable exception = loadTask.getException();
            if (exception instanceof SocketTimeoutException || exception.getMessage().contains("timeout")) {
                // 3. 超时显示重试按钮，重试逻辑就是再次调用本方法
                loadingManager.showTimeout(() -> loadAttendanceData(pageIndex));
            } else {
                loadingManager.hide();
                CustomAlertDialog.showError("错误", "加载失败");
            }
        });
        new Thread(loadTask).start();
    }

    private AttendanceLogRequest buildRequest() {
        AttendanceLogRequest request = new AttendanceLogRequest();
        request.setKeyword(keywordField.getText().trim());
        if (startDatePicker.getValue() != null) request.setStartTime(startDatePicker.getValue() + " 00:00:00");
        if (endDatePicker.getValue() != null) request.setEndTime(endDatePicker.getValue() + " 23:59:59");
        return request;
    }

    // --- 抽屜動畫 (完全拷貝 Employee 控制器風格) ---
    private void showDrawer() {
        drawerPane.setVisible(true);
        drawerPane.setMouseTransparent(false);
        TranslateTransition tt = new TranslateTransition(Duration.millis(500), drawerContent);
        tt.setFromX(550);
        tt.setToX(0);
        tt.play();
    }

    @FXML
    private void closeDrawer() {
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

    // --- 格式化辅助 ---
    private void setupDateTimeColumnFormatting() {
        verifyTimeColumn.setCellFactory(c -> new TableCell<AttendanceLog, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime it, boolean em) {
                super.updateItem(it, em);
                setText((em || it == null) ? null : AppConstants.dateTimeFormatter(it, AppConstants.YYYY_MM_DD_HH_mm_SS));
            }
        });
    }

    private void setupVerifyTypeColumnFormatting() {
        verifyTypeColumn.setCellFactory(c -> new TableCell<AttendanceLog, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else {
                    String type = String.valueOf(item);
                    switch (type) {
                        case "1":
                            setText("指紋");
                            break;
                        case "15":
                            setText("人臉");
                            break;
                        case "4":
                            setText("卡片");
                            break;
                        default:
                            setText("其他(" + type + ")");
                    }
                }
            }
        });
    }

    /**
     * 设置日期控件：禁止手动输入，点击文本框自动弹出日历
     */
    private void setupDatePickerInteractions(DatePicker datePicker) {
        // 1. 禁止手动输入
        datePicker.setEditable(false);

        // 2. 获取内部文本框 (TextField)
        TextField editor = datePicker.getEditor();

        // 3. 监听鼠标点击事件
        editor.setOnMouseClicked(event -> {
            if (!datePicker.isShowing()) {
                datePicker.show(); // 点击文本框区域时显示日历面板
            }
        });

        // 4. (可选) 鼠标进入时光标变更为手型，增强交互感
        editor.setCursor(javafx.scene.Cursor.HAND);
    }

    @FXML
    private void handleSearch() {
        updatePaginationMetadata();
    }

    @FXML
    private void handleReset() {
        keywordField.clear();
        startDatePicker.setValue(null);
        endDatePicker.setValue(null);
        handleSearch();
    }
}