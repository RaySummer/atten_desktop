package com.ray.atten.desktop.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ray.atten.desktop.dto.OaEmployeeQueryRequest;
import com.ray.atten.desktop.dto.PageResponse;
import com.ray.atten.desktop.model.OaEmployee;
import com.ray.atten.desktop.service.OaEmployeeService;
import com.ray.atten.desktop.utils.AppConstants;
import com.ray.atten.desktop.utils.ConfigRepo;
import com.ray.atten.desktop.utils.WindowUtils;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class EmployeeListController {

    @Autowired
    private OaEmployeeService oaEmployeeService;

    @Autowired
    private ApplicationContext springContext; // 需要確保 MainController 有這個依賴

    public EmployeeListController() {
        // 保持無參構造函數為空
    }

    // --- 核心分頁數據 ---
    // --- 核心分頁數據 (遠程獲取) ---
    private final ObservableList<Integer> pageSizeOptions =
            FXCollections.observableArrayList(10, 20, 50, 100);

    // 總記錄數 (從遠程服務返回的 PageResponse 中獲取)
    private long totalRecords = 0;

    // 排序狀態
    private String currentSortBy = "createTime";
    private String currentSortOrder = "DESC";
    // 追蹤上次點擊的列，用於處理連續點擊同一列
    private String lastClickedSortBy = "createTime";

    private int currentPageSize = AppConstants.DEFAULT_PAGE_SIZE;

    // --- FXML 控件注入 (只保留 FXML 中存在的) ---
    @FXML
    private TextField queryField;
    @FXML
    private Label statusLabel;
    @FXML
    private Pagination pagination;
    @FXML
    private ComboBox<Integer> pageSizeComboBox;
    @FXML
    private ChoiceBox<String> statusChoiceBox;
    @FXML
    private Button batchSyncButton;

    // 狀態變量 (用於存儲 ChoiceBox 的選擇值)
    // Boolean 類型：true=在職, false=離職, null=全部
    private Boolean currentInServiceStatus = null;

    // --- TableView 相關控件 (現在需要在 Controller 中手動創建) ---
    private TableView<OaEmployee> employeeTable;
    // 實體類字段（為排序做準備）
    private TableColumn<OaEmployee, String> pinColumn;
    private TableColumn<OaEmployee, String> nameColumn;
    private TableColumn<OaEmployee, String> deptColumn;
    private TableColumn<OaEmployee, Boolean> inServiceColumn; // 增加在職狀態列，方便排序
    private TableColumn<OaEmployee, LocalDateTime> entryDateColumn; // 增加入職日期列
    private TableColumn<OaEmployee, LocalDateTime> createTimeColumn;
    private TableColumn<OaEmployee, Void> actionColumn;
    private TableColumn<OaEmployee, Integer> rowNumberColumn; // 新增行號列


    @FXML
    public void initialize() {
        createTableViewStructure();
        setupActionColumn();

        // 【新增】格式化列的顯示
        setupInServiceColumnFormatting(); // 格式化 在職/離職 顯示
        setupDateTimeColumnFormatting(entryDateColumn); // 格式化 入職日期
        setupDateTimeColumnFormatting(createTimeColumn); // 格式化 創建時間

        setupCenterAlignmentForTextColumn(pinColumn);
        setupCenterAlignmentForTextColumn(nameColumn);
        setupCenterAlignmentForTextColumn(deptColumn);

        // 【重要】設置 TableView 啟用多選模式
        employeeTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        setupPaginationAndControls();
        setupColumnSorting(); // 【新增】設置列點擊排序

        // 【新增】初始化狀態選擇框
        setupStatusChoiceBox();

        // 首次加載，使用默認條件，只需要更新分頁元數據
        updatePaginationMetadata();
        statusLabel.setText("初始數據結構已加載。");
    }

    /**
     * 【新增】設置 TableColumn 的點擊事件，觸發排序遠程查詢。
     */
    private void setupColumnSorting() {

        employeeTable.getSortOrder().addListener((ListChangeListener<TableColumn<OaEmployee, ?>>) c -> {

            System.out.println("--- 排序事件觸發! (雙擊或狀態改變) ---");

            // 檢查當前排序列表是否為空 (這發生在舊排序被移除，新排序未添加的瞬間，我們需要忽略它)
            if (employeeTable.getSortOrder().isEmpty()) {
                return;
            }

            // 獲取當前用戶點擊的列 (總是列表中的第一項)
            TableColumn<OaEmployee, ?> sortColumn = employeeTable.getSortOrder().get(0);
            String newSortBy = getPropertyNameFromColumn(sortColumn);

            // 如果點擊了不可排序的列，則使用默認排序
            if (newSortBy == null) {
                currentSortBy = "createTime";
                currentSortOrder = "DESC";
                loadEmployeeData(pagination.getCurrentPageIndex());
                return;
            }

            // 1. 判斷是否為同一列的連續點擊
            if (newSortBy.equals(lastClickedSortBy)) {
                // 是同一列：反轉排序方向
                currentSortOrder = "ASC".equals(currentSortOrder) ? "DESC" : "ASC";
            } else {
                // 不是同一列：設置新的排序列，並將方向默認為 ASC (或您偏好的 DESC)
                currentSortBy = newSortBy;
                currentSortOrder = "ASC"; // 默認設置為 ASC
            }

            // 2. 更新 TableView 的箭頭顯示 (手動設置，確保 UI 反映邏輯)
            sortColumn.setSortType("ASC".equals(currentSortOrder) ?
                    TableColumn.SortType.ASCENDING :
                    TableColumn.SortType.DESCENDING);

            // 3. 更新追蹤變量
            lastClickedSortBy = newSortBy;

            // 4. 觸發遠程加載
            System.out.println("最終發送參數: By=" + currentSortBy + ", Order=" + currentSortOrder);
            loadEmployeeData(pagination.getCurrentPageIndex());
        });
    }

    //设置允许排序的列
    private String getPropertyNameFromColumn(TableColumn<OaEmployee, ?> column) {
        if (column == pinColumn) return "pin";
        if (column == inServiceColumn) return "inService";
        if (column == entryDateColumn) return "entryDate";
        if (column == createTimeColumn) return "createTime";
        return null;
    }

    /**
     * 【修正版】配置 ComboBox 和 Pagination 的初始化邏輯。
     */
    private void setupPaginationAndControls() {
        pageSizeComboBox.setItems(pageSizeOptions);
        pageSizeComboBox.setValue(AppConstants.DEFAULT_PAGE_SIZE);

        pageSizeComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal != oldVal) {
                currentPageSize = newVal;
                // 重新設置總頁數和當前頁索引，然後觸發數據加載
                updatePaginationMetadata();
            }
        });

        // 設置分頁工廠：每次切換頁面時，Pagination 會調用這個方法
        pagination.setPageFactory(this::createPage);
    }

    /**
     * 【修正版】根據當前頁索引，向遠程服務請求精確的頁面數據。
     *
     * @param pageIndex 當前頁索引 (從 0 開始)
     * @return 包含當前頁數據的 TableView
     */
    private Node createPage(int pageIndex) {
        // 每次切換頁面時，執行數據加載
        loadEmployeeData(pageIndex);

        // 返回包含 TableView 的 AnchorPane
        AnchorPane anchorPane = new AnchorPane(employeeTable);
        AnchorPane.setTopAnchor(employeeTable, 0.0);
        AnchorPane.setBottomAnchor(employeeTable, 0.0);
        AnchorPane.setLeftAnchor(employeeTable, 0.0);
        AnchorPane.setRightAnchor(employeeTable, 0.0);
        return anchorPane;
    }

    @FXML
    private void handleSearch() {
        // 查詢時，重置到第一頁 (索引 0)
        pagination.setCurrentPageIndex(0);
        // 由於設置 CurrentPageIndex 會觸發 createPage(0)，所以無需額外調用 loadEmployeeData(0);
        // 但我們需要先更新總頁數，以防查詢結果數量變化
        updatePaginationMetadata();
        // 狀態標籤會在 loadEmployeeData(0) 完成後更新
    }

    /**
     * 【修正版】用於首次加載或查詢條件變更時，僅獲取總記錄數，更新 Pagination 的 PageCount。
     * 然後由 Pagination 的 PageFactory 負責加載第一頁。
     */
    private void updatePaginationMetadata() {
        // 獲取查詢關鍵詞
        String query = queryField.getText().trim();

        // 構建一個只用於獲取總數的請求 (PageSize=1, PageNum=0)
        OaEmployeeQueryRequest request = new OaEmployeeQueryRequest();
        request.setKeyword(query);
        request.setInService(currentInServiceStatus);

        request.setPageSize(currentPageSize);
        request.setPageNum(1); // 請求第 1 頁，目的是獲取總頁數和總記錄數
        request.setSortBy(currentSortBy);
        request.setSortOrder(currentSortOrder);

        // 【重要檢查點】：打印完整的請求 JSON
        try {
            ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule()); // 確保 ObjectMapper 包含 JSR310 模塊
            String json = mapper.writeValueAsString(request);
            System.out.println("Request Json (Metadata) ->> " + json);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 必須在 JavaFX Application Thread 以外執行遠程調用
        Task<PageResponse<OaEmployee>> metadataTask = new Task<PageResponse<OaEmployee>>() {
            // ------------------------------------------------^^^^^^^^^^^^^^^^^^^^^ 顯式指定泛型
            @Override
            protected PageResponse<OaEmployee> call() throws Exception {
                // 遠程請求數據
                return oaEmployeeService.queryEmployees(request);
            }
        };

        metadataTask.setOnSucceeded(e -> {
            PageResponse<OaEmployee> response = metadataTask.getValue();
            if (response != null) {
                // 設置新的總記錄數
                totalRecords = response.getTotalElements();

                // 1. 計算新的總頁數
                int newPageCount = (int) Math.ceil((double) totalRecords / currentPageSize);
                pagination.setPageCount(Math.max(1, newPageCount));

                // 2. 確保當前頁碼在範圍內 (如果結果變少)
                if (pagination.getCurrentPageIndex() >= newPageCount) {
                    pagination.setCurrentPageIndex(Math.max(0, newPageCount - 1));
                }

                // 3. 觸發當前頁的數據加載 (如果 PageIndex 沒有改變，需要手動刷新)
                loadEmployeeData(pagination.getCurrentPageIndex());
            }
        });

        metadataTask.setOnFailed(e -> statusLabel.setText("錯誤：無法獲取分頁元數據。"));

        // 使用新線程執行
        new Thread(metadataTask).start();
    }

    /**
     * 【修正版】加載指定頁碼的員工數據（遠程分頁）。
     *
     * @param pageIndex 頁索引 (從 0 開始)
     */
    private void loadEmployeeData(int pageIndex) {
        // 獲取查詢關鍵詞
        String query = queryField.getText().trim();

        // 1. 構造帶有完整分頁、過濾、排序信息的請求
        OaEmployeeQueryRequest request = new OaEmployeeQueryRequest();
        request.setKeyword(query);
        request.setInService(currentInServiceStatus);
        request.setPageNum(pageIndex + 1); // JPA 服務端需要 PageNum 從 1 開始
        request.setPageSize(currentPageSize);
        request.setSortBy(currentSortBy);
        request.setSortOrder(currentSortOrder);

        // 【重要檢查點】：打印完整的請求 JSON
        try {
            ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule()); // 確保 ObjectMapper 包含 JSR310 模塊
            String json = mapper.writeValueAsString(request);
            System.out.println("Request Json (Metadata) ->> " + json);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2. 使用 JavaFX Task 進行異步調用
        Task<PageResponse<OaEmployee>> loadDataTask = new Task<PageResponse<OaEmployee>>() {
            // ------------------------------------------------^^^^^^^^^^^^^^^^^^^^^ 顯式指定泛型
            @Override
            protected PageResponse<OaEmployee> call() throws Exception {
                // 遠程請求數據
                return oaEmployeeService.queryEmployees(request);
            }
        };

        loadDataTask.setOnSucceeded(e -> {
            PageResponse<OaEmployee> response = loadDataTask.getValue();
            if (response != null) {
                // 1. 更新 TableView 數據
                employeeTable.setItems(FXCollections.observableArrayList(response.getContent()));
                employeeTable.refresh();

                // 2. 更新總記錄數 (如果它在加載過程中變了，雖然 metadata 已經更新過，但這裡是保險措施)
                totalRecords = response.getTotalElements();

                // 3. 更新狀態欄
                statusLabel.setText(String.format("頁面 %d/%d 加載完成。總記錄數: %d",
                        pageIndex + 1, pagination.getPageCount(), totalRecords));
            } else {
                employeeTable.getItems().clear();
                statusLabel.setText("數據加載失敗，遠程服務返回空。");
            }
        });

        loadDataTask.setOnFailed(e -> {
            // 處理網絡或解析異常
            employeeTable.getItems().clear();
            statusLabel.setText("錯誤：加載數據時發生異常。");
        });

        // 使用新線程執行遠程調用
        new Thread(loadDataTask).start();
    }

    /**
     * 手動創建 TableView 及其所有列，並設置屬性綁定。
     */
    private void createTableViewStructure() {
        // 實例化 TableView
        employeeTable = new TableView<>();
        employeeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        rowNumberColumn = new TableColumn<>();
        // 實例化 TableColumn
        pinColumn = new TableColumn<>("工号");
        nameColumn = new TableColumn<>("姓名");
        deptColumn = new TableColumn<>("部門");
        actionColumn = new TableColumn<>("操作");
        inServiceColumn = new TableColumn<>("在職");
        entryDateColumn = new TableColumn<>("入職日期");
        createTimeColumn = new TableColumn<>("創建時間");

        rowNumberColumn.setMinWidth(30);
        rowNumberColumn.setMaxWidth(30); // 設置固定寬度，防止行號被壓縮
        rowNumberColumn.setResizable(false);
        rowNumberColumn.setSortable(false); // 行號列通常不需要排序

        // 設置列寬和不可調整
        pinColumn.setMinWidth(80);
        nameColumn.setMinWidth(150);
        deptColumn.setMinWidth(80);
        inServiceColumn.setMinWidth(80);
        entryDateColumn.setMinWidth(150);
        createTimeColumn.setMinWidth(150);
        actionColumn.setMinWidth(180); // 增加寬度以容納兩個按鈕
        actionColumn.setResizable(false);

        // 設置列的數據綁定 (必須使用 PropertyValueFactory)
        pinColumn.setCellValueFactory(new PropertyValueFactory<>("pin"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        deptColumn.setCellValueFactory(new PropertyValueFactory<>("dept"));
        inServiceColumn.setCellValueFactory(new PropertyValueFactory<>("inService"));
        entryDateColumn.setCellValueFactory(new PropertyValueFactory<>("entryDate"));
        createTimeColumn.setCellValueFactory(new PropertyValueFactory<>("createTime"));

        // 設置 CellFactory 邏輯
        rowNumberColumn.setCellFactory(col -> new TableCell<OaEmployee, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setAlignment(Pos.CENTER); // 確保行號居中

                if (empty) {
                    setText(null);
                } else {
                    // 獲取當前行在 TableView 中的索引 (從 0 開始)
                    int rowIndex = getIndex();

                    // 獲取當前頁碼 (通常從 0 開始)
                    int currentPage = pagination.getCurrentPageIndex();

                    // 獲取當前頁面大小 (例如 10)
                    int pageSize = currentPageSize; // 假設您有這個全局變量

                    // 計算全局行號
                    // 行號 = (當前頁碼 * 頁面大小) + 當前行索引 + 1
                    int rowNumber = (currentPage * pageSize) + rowIndex + 1;

                    setText(String.valueOf(rowNumber));
                }
            }
        });

        // 將所有列添加到 TableView
        employeeTable.getColumns().addAll(rowNumberColumn, pinColumn, nameColumn, deptColumn, inServiceColumn, entryDateColumn, createTimeColumn, actionColumn);

        // 【核心修復點】手動設置初始排序，這通常能激活 TableView 的內部排序事件處理機制
        // 設置默認排序：按 createTime 降序
        employeeTable.getSortOrder().add(createTimeColumn);

        // 設置列的默認排序圖標
        entryDateColumn.setSortType(TableColumn.SortType.DESCENDING);
//        employeeTable.getSortOrder().add(entryDateColumn);
        createTimeColumn.setSortType(TableColumn.SortType.DESCENDING);
        pinColumn.setSortType(TableColumn.SortType.DESCENDING);
        pinColumn.setSortable(true);
        inServiceColumn.setSortable(true);
        entryDateColumn.setSortable(true);
        createTimeColumn.setSortable(true);

//        // 【關鍵】：將行號列插入到 TableView 的第一位
//        employeeTable.getColumns().add(0, rowNumberColumn); // 插入到索引 0
    }


    /**
     * 設置操作列的 CellFactory。
     */
    private void setupActionColumn() {
        actionColumn.setCellFactory(col -> new TableCell<OaEmployee, Void>() {
            private final Button detailButton = new Button("錄入詳情");
            private final Button syncButton = new Button("同步");
            private final HBox pane = new HBox(10, detailButton, syncButton);

            {
                // --- 詳情按鈕設置 ---
                detailButton.setOnAction(event -> {
                    OaEmployee employee = getTableView().getItems().get(getIndex());
                    openDetailWindow(employee);
                });
                detailButton.setPrefWidth(75);
                // 【修改点】移除 setStyle，改用 StyleClass
                detailButton.getStyleClass().add("action-btn-detail");

                // --- 同步按鈕設置 ---
                syncButton.setOnAction(event -> {
                    OaEmployee employee = getTableView().getItems().get(getIndex());
                    handleSynchronize(employee);
                });
                syncButton.setPrefWidth(75);
                // 【修改点】移除 setStyle，改用 StyleClass
                syncButton.getStyleClass().add("action-btn-sync");

                pane.setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(pane);
                }
            }
        });
    }


    private void openDetailWindow(OaEmployee employee) {
        System.out.println("正在打開員工詳情窗口: " + employee.getName() + " (PIN: " + employee.getPin() + ")");

        if (employee == null) {
            new Alert(Alert.AlertType.WARNING, "請先選擇一個員工。").showAndWait();
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/EmployeeDetailView.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Parent root = loader.load();

            EmployeeDetailController controller = loader.getController();
            controller.setEmployeeInfo(employee);

            Stage stage = new Stage();
            stage.setTitle("員工錄入詳情");

            // --- 应用 WindowUtils ---
            Scene scene = new Scene(root);
            WindowUtils.applyCurrentTheme(scene);
            stage.setScene(scene);
            // -----------------------

            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void applyCurrentThemeToStage(Stage stage) {
        // 获取当前 ConfigRepo 记录的主题（假设你已经按照之前的建议在 ConfigRepo 存了主题）
        String currentTheme = ConfigRepo.getTheme();
        String cssPath = currentTheme.equals("dark") ? "/css/dark.css" : "/css/light.css";

        Scene scene = stage.getScene();
        if (scene != null) {
            scene.getStylesheets().clear();
            scene.getStylesheets().add(getClass().getResource(cssPath).toExternalForm());
        }
    }

    /**
     * 設置 inServiceColumn 的 CellFactory，將布爾值轉換為中文顯示。
     */
    private void setupInServiceColumnFormatting() {
        inServiceColumn.setCellValueFactory(new PropertyValueFactory<>("inService"));
        inServiceColumn.setCellFactory(column -> {
            return new TableCell<OaEmployee, Boolean>() {
                @Override
                protected void updateItem(Boolean item, boolean empty) {
                    super.updateItem(item, empty);
                    setAlignment(Pos.CENTER);
                    // 【修改点】先移除所有状态类，防止复用导致的样式混乱
                    getStyleClass().removeAll("status-active", "status-inactive");

                    if (empty || item == null) {
                        setText(null);
                    } else {
                        if (item) {
                            setText("在職");
                            getStyleClass().add("status-active");
                        } else {
                            setText("離職");
                            getStyleClass().add("status-inactive");
                        }
                    }
                }
            };
        });
    }

    /**
     * 設置日期列的 CellFactory，將 LocalDateTime 格式化為 "yyyy-MM-dd HH:mm:ss"。
     *
     * @param column 要格式化的 TableColumn
     */
    private void setupDateTimeColumnFormatting(TableColumn<OaEmployee, LocalDateTime> column) {

        column.setCellFactory(col -> {
            return new TableCell<OaEmployee, LocalDateTime>() {
                @Override
                protected void updateItem(LocalDateTime item, boolean empty) {
                    super.updateItem(item, empty);
                    setAlignment(Pos.CENTER);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        // 將 LocalDateTime 格式化為字符串
                        setText(AppConstants.dateTimeFormatter(item));
                    }
                }
            };
        });
    }

    /**
     * 輔助方法：為簡單文本列設置居中對齊。
     */
    private void setupCenterAlignmentForTextColumn(TableColumn<OaEmployee, String> column) {
        column.setCellFactory(col -> new TableCell<OaEmployee, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    // 【核心】設置居中對齊
                    setAlignment(Pos.CENTER);
                }
            }
        });
    }

    /**
     * 設置狀態選擇框的選項和監聽器。
     */
    private void setupStatusChoiceBox() {
        // 設置選項：全部(null), 在職(true), 離職(false)
        statusChoiceBox.getItems().addAll("全部", "在職", "離職");
        statusChoiceBox.setValue("全部"); // 設置默認值

        // 監聽選擇框的變化
        statusChoiceBox.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            switch (newValue) {
                case "在職":
                    currentInServiceStatus = true;
                    break;
                case "離職":
                    currentInServiceStatus = false;
                    break;
                case "全部":
                default:
                    currentInServiceStatus = null;
                    break;
            }

            System.out.println("ChoiceBox 狀態變更 -> currentInServiceStatus: " + currentInServiceStatus);
        });
    }


    /**
     * 【預留方法】執行批量同步的具體 API 調用。
     */
    private void performBatchSync(ObservableList<OaEmployee> employees) {
        // 獲取所有選中員工的 PIN 碼
        String pins = employees.stream()
                .map(OaEmployee::getPin)
                .collect(Collectors.joining(", "));

        statusLabel.setText("正在批量同步以下 PIN 碼的員工: " + pins);

        // TODO: 在這裡實現異步調用 oaEmployeeService.batchSynchronize(pins) 的邏輯
        // 這是調用 API 的預留位置

        Alert finalAlert = new Alert(Alert.AlertType.INFORMATION);
        finalAlert.setTitle("同步結果");
        finalAlert.setHeaderText("批量同步請求已發送");
        finalAlert.setContentText("選中 " + employees.size() + " 位員工的數據正在後台同步中...");
        finalAlert.showAndWait();
    }

    /**
     * 【整合方法】打開同步配置彈窗。
     *
     * @param employees 要同步的員工列表
     */
    private void openSyncConfigDialog(List<OaEmployee> employees) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/view/SyncGroupView.fxml"));
            loader.setControllerFactory(springContext::getBean);

            Parent root = loader.load();

            SyncGroupController controller = loader.getController();
            controller.setEmployeesToSync(employees);

            Stage stage = new Stage();
            stage.setTitle("同步到考勤組配置");

            // --- 核心修复：为新窗口应用当前主题 ---
            Scene scene = new Scene(root);

            WindowUtils.applyCurrentTheme(scene); // 一行搞定主题同步

            stage.setScene(scene);
            // ------------------------------------

            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setResizable(false);
            stage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            Alert error = new Alert(Alert.AlertType.ERROR, "無法加載同步配置界面。", ButtonType.OK);
            error.showAndWait();
        }
    }

    // 修改批量同步方法
    @FXML
    private void handleBatchSynchronize() {
        // 獲取選中的員工列表
        ObservableList<OaEmployee> selectedEmployees = employeeTable.getSelectionModel().getSelectedItems();

        if (selectedEmployees.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("操作提示");
            alert.setHeaderText(null);
            alert.setContentText("請選擇至少一位員工進行批量同步。");
            alert.showAndWait();
            return;
        }

        // 調用新的彈窗方法，傳遞選中的員工列表
        // 使用 new ArrayList<>(...) 複製列表，以防在彈窗內進行修改
        openSyncConfigDialog(new ArrayList<>(selectedEmployees));
    }

    // 修改單個同步方法 (在 TableCell 內調用)
    private void handleSynchronize(OaEmployee employee) {
        // 這裡可以跳過簡單確認，直接打開配置彈窗
        openSyncConfigDialog(Collections.singletonList(employee));
    }
//
//    @FXML
//    public void openServerSettings() {
//        try {
//            // 弹出模态窗口（或者直接切换场景）
//            Stage currentStage = (Stage) someNode.getScene().getWindow();
//            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ServerSetupView.fxml"));
//            loader.setControllerFactory(springContext::getBean);
//            Parent root = loader.load();
//
//            // 我们可以在加载设置页时，把当前的配置填进去
//            // ServerSetupController controller = loader.getController();
//            // controller.preFill(AppConstants.API_BASE_URL);
//
//            currentStage.setScene(new Scene(root));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

}
