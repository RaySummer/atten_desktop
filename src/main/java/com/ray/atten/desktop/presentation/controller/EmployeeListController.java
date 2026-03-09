package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.dto.OaEmployeeQueryRequest;
import com.ray.atten.desktop.dto.PageResponse;
import com.ray.atten.desktop.model.OaEmployee;
import com.ray.atten.desktop.service.OaEmployeeService;
import com.ray.atten.desktop.utils.AppConstants;
import com.ray.atten.desktop.utils.CustomAlertDialog;
import com.ray.atten.desktop.utils.LoadingManager;
import javafx.animation.TranslateTransition;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class EmployeeListController {

    @Autowired
    private OaEmployeeService oaEmployeeService;
    @Autowired
    private ApplicationContext springContext;
    @Autowired
    private LoadingManager loadingManager;

    // --- 侧边抽屉相关控件 ---
    @FXML private AnchorPane drawerPane;
    @FXML private VBox drawerContent;
    @FXML private StackPane detailContainer;

    // --- FXML 控件注入 ---
    @FXML private TextField queryField;
    @FXML private Label statusLabel;
    @FXML private Pagination pagination;
    @FXML private ComboBox<Integer> pageSizeComboBox;
    @FXML private ChoiceBox<String> statusChoiceBox;
    @FXML private ChoiceBox<String> fingerprintChoiceBox;
    @FXML private ChoiceBox<String> photoChoiceBox;
    @FXML private Button batchSyncButton;
    @FXML private Button printBadgeButton;

    // --- TableView 相關控件 ---
    private TableView<OaEmployee> employeeTable;
    private TableColumn<OaEmployee, String> pinColumn, nameColumn, deptColumn, officeLocation;
    private TableColumn<OaEmployee, String> fingerprintColumn, photoColumn;
    private TableColumn<OaEmployee, Boolean> inServiceColumn;
    private TableColumn<OaEmployee, LocalDateTime> entryDateColumn;
    private TableColumn<OaEmployee, Void> actionColumn;
    private TableColumn<OaEmployee, Integer> rowNumberColumn;

    // --- 分页与排序数据 ---
    private final ObservableList<Integer> pageSizeOptions = FXCollections.observableArrayList(10, 20, 50, 100);
    private long totalRecords = 0;
    private String currentSortBy = "createTime";
    private String currentSortOrder = "DESC";
    private String lastClickedSortBy = "createTime";
    private int currentPageSize = AppConstants.DEFAULT_PAGE_SIZE;
    private Boolean currentInServiceStatus = null;
    private Boolean hasFingerprint = null;
    private Boolean hasPhoto = null;

    @FXML
    public void initialize() {
        createTableViewStructure();
        setupActionColumn();
        setupInServiceColumnFormatting();
        setupDateTimeColumnFormatting(entryDateColumn);
        setupCenterAlignmentForTextColumn(pinColumn);
        setupCenterAlignmentForTextColumn(nameColumn);
        setupCenterAlignmentForTextColumn(deptColumn);
        setupCenterAlignmentForTextColumn(officeLocation);

        employeeTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        setupPaginationAndControls();
        setupColumnSorting();

        // 初始化三个 ChoiceBox
        setupStatusChoiceBox();
        setupSearchFilters(); // 处理指纹和照片

        updatePaginationMetadata();
        statusLabel.setText("数据列表已就绪。");
    }

    private void createTableViewStructure() {
        employeeTable = new TableView<>();
        employeeTable.getStyleClass().add("custom-table-view");
        employeeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        rowNumberColumn = new TableColumn<>("序号");
        pinColumn = new TableColumn<>("工号");
        nameColumn = new TableColumn<>("姓名");
        deptColumn = new TableColumn<>("部门");
        officeLocation = new TableColumn<>("办公地点");
        fingerprintColumn = new TableColumn<>("指纹");
        photoColumn = new TableColumn<>("照片");
        inServiceColumn = new TableColumn<>("是否在职");
        entryDateColumn = new TableColumn<>("入职日期");
        actionColumn = new TableColumn<>("操作");

        // 设置列宽
        rowNumberColumn.setMinWidth(50); rowNumberColumn.setMaxWidth(50);
        fingerprintColumn.setMinWidth(65); fingerprintColumn.setMaxWidth(80);
        photoColumn.setMinWidth(65); photoColumn.setMaxWidth(80);
        entryDateColumn.setMinWidth(120);
        actionColumn.setMinWidth(180);

        // 绑定字段
        pinColumn.setCellValueFactory(new PropertyValueFactory<>("pin"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        deptColumn.setCellValueFactory(new PropertyValueFactory<>("dept"));
        officeLocation.setCellValueFactory(new PropertyValueFactory<>("officeLocation"));
        fingerprintColumn.setCellValueFactory(new PropertyValueFactory<>("fingerprint"));
        photoColumn.setCellValueFactory(new PropertyValueFactory<>("photoBase64"));
        inServiceColumn.setCellValueFactory(new PropertyValueFactory<>("inService"));
        entryDateColumn.setCellValueFactory(new PropertyValueFactory<>("entryDate"));

        // 格式化“是/否”列
        setupBinaryStatusColumnFormatting(fingerprintColumn);
        setupBinaryStatusColumnFormatting(photoColumn);

        rowNumberColumn.setCellFactory(col -> new TableCell<OaEmployee, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setText(null);
                else setText(String.valueOf((pagination.getCurrentPageIndex() * currentPageSize) + getIndex() + 1));
            }
        });

        employeeTable.getColumns().addAll(rowNumberColumn, pinColumn, nameColumn, deptColumn,
                fingerprintColumn, photoColumn, officeLocation, inServiceColumn, entryDateColumn, actionColumn);
    }

    private void setupSearchFilters() {
        // 指纹筛选
        fingerprintChoiceBox.getItems().addAll("全部", "已有", "未录");
        fingerprintChoiceBox.setValue("全部");
        fingerprintChoiceBox.getSelectionModel().selectedItemProperty().addListener((o, ol, nv) -> {
            hasFingerprint = "已有".equals(nv) ? Boolean.TRUE : ("未录".equals(nv) ? Boolean.FALSE : null);
            updatePaginationMetadata();
        });

        photoChoiceBox.getItems().addAll("全部", "已有", "未录");
        photoChoiceBox.setValue("全部");
        photoChoiceBox.getSelectionModel().selectedItemProperty().addListener((o, ol, nv) -> {
            hasPhoto = "已有".equals(nv) ? Boolean.TRUE : ("未录".equals(nv) ? Boolean.FALSE : null);
            updatePaginationMetadata();
        });
    }

    private void fillRequestParams(OaEmployeeQueryRequest request) {
        request.setKeyword(queryField.getText().trim());
        // 直接传递 Boolean 对象，后端 QueryRequest 里的字段也必须是 Boolean 类型而非 boolean
        request.setInService(currentInServiceStatus);
        request.setHasFingerprint(hasFingerprint);
        request.setHasPhoto(hasPhoto);

        request.setPageSize(currentPageSize);
        request.setSortBy(currentSortBy);
        request.setSortOrder(currentSortOrder);
    }
    private void updatePaginationMetadata() {
        loadingManager.show("正在加载中.....");
        OaEmployeeQueryRequest request = new OaEmployeeQueryRequest();
        fillRequestParams(request);
        request.setPageNum(1);

        Task<PageResponse<OaEmployee>> metadataTask = new Task<PageResponse<OaEmployee>>() {
            @Override
            protected PageResponse<OaEmployee> call() throws Exception {
                return oaEmployeeService.queryEmployees(request);
            }
        };

        metadataTask.setOnSucceeded(e -> {
            PageResponse<OaEmployee> response = metadataTask.getValue();
            if (response != null) {
                totalRecords = response.getTotalElements();
                pagination.setPageCount(Math.max(1, (int) Math.ceil((double) totalRecords / currentPageSize)));
                loadEmployeeData(pagination.getCurrentPageIndex());
            }
        });

        metadataTask.setOnFailed(e -> {
            loadingManager.hide();
            CustomAlertDialog.showError("错误", "加载元数据失败");
        });

        new Thread(metadataTask).start();
    }

    private void loadEmployeeData(int pageIndex) {
        loadingManager.show("正在加载中.....");
        OaEmployeeQueryRequest request = new OaEmployeeQueryRequest();
        fillRequestParams(request);
        request.setPageNum(pageIndex + 1);

        Task<PageResponse<OaEmployee>> loadTask = new Task<PageResponse<OaEmployee>>() {
            @Override
            protected PageResponse<OaEmployee> call() throws Exception {
                return oaEmployeeService.queryEmployees(request);
            }
        };

        loadTask.setOnSucceeded(e -> {
            loadingManager.hide();
            PageResponse<OaEmployee> res = loadTask.getValue();
            if (res != null) {
                employeeTable.setItems(FXCollections.observableArrayList(res.getContent()));
                statusLabel.setText(String.format("页面 %d/%d 加载完成。总记录: %d",
                        pageIndex + 1, pagination.getPageCount(), res.getTotalElements()));
            }
        });

        loadTask.setOnFailed(e -> {
            loadingManager.hide();
            CustomAlertDialog.showError("错误", "加载员工数据失败");
        });

        new Thread(loadTask).start();
    }

    private void setupBinaryStatusColumnFormatting(TableColumn<OaEmployee, String> col) {
        col.setCellFactory(column -> new TableCell<OaEmployee, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setAlignment(Pos.CENTER);
                if (empty) {
                    setText(null);
                    setStyle("");
                } else {
                    boolean exists = (item != null && !item.trim().isEmpty());
                    setText(exists ? "是" : "否");
                    setStyle(exists ? "-fx-text-fill: #2ecc71; -fx-font-weight: bold;" : "-fx-text-fill: #ffa89c;");
                }
            }
        });
    }

    // --- 其余 UI 辅助逻辑 (状态、日期、居中、分页、排序、抽屉等) ---

    private void setupStatusChoiceBox() {
        statusChoiceBox.getItems().addAll("全部", "在职", "离职");
        statusChoiceBox.setValue("全部");
        statusChoiceBox.getSelectionModel().selectedItemProperty().addListener((o, ol, nv) -> {
            currentInServiceStatus = "在职".equals(nv) ? true : ("离职".equals(nv) ? false : null);
            updatePaginationMetadata();
        });
    }

    private void setupActionColumn() {
        actionColumn.setCellFactory(col -> new TableCell<OaEmployee, Void>() {
            private final Button detailButton = new Button("录入详情");
            private final Button syncButton = new Button("同步");
            private final HBox pane = new HBox(10, detailButton, syncButton);
            {
                detailButton.getStyleClass().add("action-btn-detail");
                detailButton.setStyle("-fx-text-fill: #ffffff;");
                detailButton.setOnAction(event -> openViewInDrawer("/view/EmployeeDetailView.fxml", getTableView().getItems().get(getIndex())));
                syncButton.getStyleClass().add("action-btn-sync");
                syncButton.setStyle("-fx-text-fill: #ffffff;");
                syncButton.setOnAction(event -> openViewInDrawer("/view/SyncGroupView.fxml", getTableView().getItems().get(getIndex())));
                pane.setAlignment(Pos.CENTER);
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : pane);
            }
        });
    }

    private void setupInServiceColumnFormatting() {
        inServiceColumn.setCellFactory(column -> new TableCell<OaEmployee, Boolean>() {
            @Override protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else {
                    setText(item ? "在职" : "离职");
                    setStyle(item ? "-fx-text-fill: #2ecc71;" : "-fx-text-fill: #ff3333;");
                }
            }
        });
    }

    private void setupDateTimeColumnFormatting(TableColumn<OaEmployee, LocalDateTime> col) {
        col.setCellFactory(c -> new TableCell<OaEmployee, LocalDateTime>() {
            @Override protected void updateItem(LocalDateTime it, boolean em) {
                super.updateItem(it, em);
                setText((em || it == null) ? null : AppConstants.dateTimeFormatter(it, AppConstants.YYYY_MM_DD_HH_mm_SS));
            }
        });
    }

    private void setupCenterAlignmentForTextColumn(TableColumn<OaEmployee, String> col) {
        col.setCellFactory(c -> new TableCell<OaEmployee, String>() {
            @Override protected void updateItem(String it, boolean em) {
                super.updateItem(it, em);
                setAlignment(Pos.CENTER);
                setText((em || it == null) ? null : it);
            }
        });
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
        loadEmployeeData(pageIndex);
        AnchorPane ap = new AnchorPane(employeeTable);
        AnchorPane.setTopAnchor(employeeTable, 0.0); AnchorPane.setBottomAnchor(employeeTable, 0.0);
        AnchorPane.setLeftAnchor(employeeTable, 0.0); AnchorPane.setRightAnchor(employeeTable, 0.0);
        return ap;
    }

    private void setupColumnSorting() {
        employeeTable.getSortOrder().addListener((ListChangeListener<TableColumn<OaEmployee, ?>>) c -> {
            if (employeeTable.getSortOrder().isEmpty()) return;
            TableColumn<OaEmployee, ?> sortColumn = employeeTable.getSortOrder().get(0);
            String newSortBy = getPropertyNameFromColumn(sortColumn);
            if (newSortBy != null) {
                if (newSortBy.equals(lastClickedSortBy))
                    currentSortOrder = "ASC".equals(currentSortOrder) ? "DESC" : "ASC";
                else {
                    currentSortBy = newSortBy;
                    currentSortOrder = "ASC";
                }
                lastClickedSortBy = newSortBy;
                loadEmployeeData(pagination.getCurrentPageIndex());
            }
        });
    }

    private String getPropertyNameFromColumn(TableColumn<OaEmployee, ?> col) {
        if (col == pinColumn) return "pin";
        if (col == inServiceColumn) return "inService";
        if (col == entryDateColumn) return "entryDate";
        return null;
    }

    private void openViewInDrawer(String fxmlPath, Object data) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            loader.setControllerFactory(springContext::getBean);
            Node node = loader.load();
            Object controller = loader.getController();
            if (controller instanceof EmployeeDetailController) {
                ((EmployeeDetailController) controller).setEmployeeInfo((OaEmployee) data);
                ((EmployeeDetailController) controller).setOnCloseRequest(this::closeDrawer);
            } else if (controller instanceof SyncGroupController) {
                List<OaEmployee> employees = (data instanceof List) ? (List<OaEmployee>) data : Collections.singletonList((OaEmployee) data);
                ((SyncGroupController) controller).setEmployeesToSync(employees);
                ((SyncGroupController) controller).setOnCloseRequest(this::closeDrawer);
            } else if (controller instanceof BadgePrintController) {
                List<OaEmployee> employees = (data instanceof List) ? (List<OaEmployee>) data : Collections.singletonList((OaEmployee) data);
                ((BadgePrintController) controller).setSelectedEmployees(employees);
                ((BadgePrintController) controller).setOnCloseRequest(this::closeDrawer);
            }
            detailContainer.getChildren().setAll(node);
            showDrawer();
        } catch (IOException e) {
            CustomAlertDialog.showError("", "无法加载界面: " + fxmlPath);
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

    @FXML private void closeDrawer() {
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

    @FXML private void handleSearch() { updatePaginationMetadata(); }

    @FXML private void handleBatchSynchronize() {
        ObservableList<OaEmployee> selected = employeeTable.getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) { CustomAlertDialog.showWarning(null, "请选择至少一位员工。"); return; }
        openViewInDrawer("/view/SyncGroupView.fxml", new ArrayList<>(selected));
    }

    @FXML private void handlePrintBadge() {
        ObservableList<OaEmployee> selected = employeeTable.getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) { CustomAlertDialog.showWarning(null, "请选择至少一位员工。"); return; }
        openViewInDrawer("/view/BadgePrintView.fxml", new ArrayList<>(selected));
    }
}