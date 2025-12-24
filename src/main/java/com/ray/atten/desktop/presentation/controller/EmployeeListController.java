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

    // --- 侧边抽屉相关控件 ---
    @FXML
    private AnchorPane drawerPane;
    @FXML
    private VBox drawerContent;
    @FXML
    private StackPane detailContainer;

    // --- FXML 控件注入 ---
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

    // --- TableView 相關控件 ---
    private TableView<OaEmployee> employeeTable;
    private TableColumn<OaEmployee, String> pinColumn, nameColumn, deptColumn, officeLocation;
    private TableColumn<OaEmployee, Boolean> inServiceColumn;
    private TableColumn<OaEmployee, LocalDateTime> entryDateColumn, createTimeColumn;
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

    @Autowired
    private LoadingManager loadingManager;

    @FXML
    public void initialize() {
        createTableViewStructure();
        setupActionColumn();
        setupInServiceColumnFormatting();
        setupDateTimeColumnFormatting(entryDateColumn);
//        setupDateTimeColumnFormatting(createTimeColumn);
        setupCenterAlignmentForTextColumn(pinColumn);
        setupCenterAlignmentForTextColumn(nameColumn);
        setupCenterAlignmentForTextColumn(deptColumn);
        setupCenterAlignmentForTextColumn(officeLocation);

        employeeTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        setupPaginationAndControls();
        setupColumnSorting();
        setupStatusChoiceBox();

        updatePaginationMetadata();
        statusLabel.setText("数据列表已就绪。");
    }

    /**
     * 【核心整合】统一的抽屉开启逻辑
     *
     * @param fxmlPath FXML路径
     * @param data     要传递的数据（单个Employee或List）
     */
    private void openViewInDrawer(String fxmlPath, Object data) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            loader.setControllerFactory(springContext::getBean);
            Node node = loader.load();

            // 根据加载的视图类型，传递不同的数据
            Object controller = loader.getController();
            if (controller instanceof EmployeeDetailController) {
                ((EmployeeDetailController) controller).setEmployeeInfo((OaEmployee) data);
                ((EmployeeDetailController) controller).setOnCloseRequest(this::closeDrawer);
            } else if (controller instanceof SyncGroupController) {
                if (data instanceof List) {
                    ((SyncGroupController) controller).setEmployeesToSync((List<OaEmployee>) data);
                } else {
                    ((SyncGroupController) controller).setEmployeesToSync(Collections.singletonList((OaEmployee) data));
                }
                ((SyncGroupController) controller).setOnCloseRequest(this::closeDrawer);
            }

            detailContainer.getChildren().setAll(node);
            showDrawer();
        } catch (IOException e) {
            e.printStackTrace();
//            new Alert(Alert.AlertType.ERROR, "無法加載界面: " + fxmlPath).showAndWait();
            CustomAlertDialog.showError("", "无法加载界面: " + fxmlPath);
        }
    }

    // 抽屉显示动画
    private void showDrawer() {
        if (drawerPane == null) return;
        drawerPane.setVisible(true);
        drawerPane.setMouseTransparent(false);

        TranslateTransition tt = new TranslateTransition(Duration.millis(500), drawerContent);
        tt.setFromX(drawerContent.getWidth() > 0 ? drawerContent.getWidth() : 550);
        tt.setToX(0);
        tt.play();
    }

    // 抽屉关闭动画
    @FXML
    private void closeDrawer() {
        TranslateTransition tt = new TranslateTransition(Duration.millis(300), drawerContent);
        tt.setToX(550); // 必须与打开时的 FromX 对应
        tt.setOnFinished(e -> {
            drawerPane.setVisible(false);
            drawerPane.setMouseTransparent(true);
            detailContainer.getChildren().clear();
        });
        tt.play();
    }

    @FXML
    private void handleOverlayClick(MouseEvent event) {
        // 点击遮罩部分（抽屉左侧区域）则关闭
        if (event.getX() < (drawerPane.getWidth() - drawerContent.getWidth())) {
            closeDrawer();
        }
    }

    /**
     * 设置操作列：详情按钮调用抽屉，同步按钮调用抽屉
     */
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

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : pane);
            }
        });
    }

    @FXML
    private void handleBatchSynchronize() {
        ObservableList<OaEmployee> selected = employeeTable.getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) {
            CustomAlertDialog.showWarning(null, "请选择至少一位员工。");
            return;
        }
        openViewInDrawer("/view/SyncGroupView.fxml", new ArrayList<>(selected));
    }

    // =========================================================================
    // 基础表格构建与分页逻辑 (保持原有)
    // =========================================================================

    private void createTableViewStructure() {
        employeeTable = new TableView<>();
        employeeTable.getStyleClass().add("custom-table-view");
        employeeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        rowNumberColumn = new TableColumn<>("序号");
        pinColumn = new TableColumn<>("工号");
        nameColumn = new TableColumn<>("姓名");
        deptColumn = new TableColumn<>("部门");
        officeLocation = new TableColumn<>("办公地点");
        inServiceColumn = new TableColumn<>("是否在职");
        entryDateColumn = new TableColumn<>("入职日期");
        actionColumn = new TableColumn<>("操作");

        rowNumberColumn.setMinWidth(50);
        rowNumberColumn.setMaxWidth(50);
//        pinColumn.setMaxWidth(100);
//        nameColumn.setMinWidth(90);
//        deptColumn.setMaxWidth(100);
//        inServiceColumn.setMaxWidth(80);
        entryDateColumn.setMinWidth(120);
        actionColumn.setMinWidth(100);

        pinColumn.setCellValueFactory(new PropertyValueFactory<>("pin"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        deptColumn.setCellValueFactory(new PropertyValueFactory<>("dept"));
        officeLocation.setCellValueFactory(new PropertyValueFactory<>("officeLocation"));
        inServiceColumn.setCellValueFactory(new PropertyValueFactory<>("inService"));
        entryDateColumn.setCellValueFactory(new PropertyValueFactory<>("entryDate"));
//        createTimeColumn.setCellValueFactory(new PropertyValueFactory<>("createTime"));

        rowNumberColumn.setCellFactory(col -> new TableCell<OaEmployee, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setText(null);
                else setText(String.valueOf((pagination.getCurrentPageIndex() * currentPageSize) + getIndex() + 1));
            }
        });

        employeeTable.getColumns().addAll(rowNumberColumn, pinColumn, nameColumn, deptColumn, officeLocation, inServiceColumn, entryDateColumn, actionColumn);
    }

    private void updatePaginationMetadata() {
        String query = queryField.getText().trim();
        OaEmployeeQueryRequest request = new OaEmployeeQueryRequest();
        request.setKeyword(query);
        request.setInService(currentInServiceStatus);
        request.setPageSize(currentPageSize);
        request.setSortBy(currentSortBy);
        request.setSortOrder(currentSortOrder);

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
        new Thread(metadataTask).start();
    }

    private void loadEmployeeData(int pageIndex) {
        loadingManager.show("正在加载中.....");
        OaEmployeeQueryRequest request = new OaEmployeeQueryRequest();
        request.setKeyword(queryField.getText().trim());
        request.setInService(currentInServiceStatus);
        request.setPageNum(pageIndex + 1);
        request.setPageSize(currentPageSize);
        request.setSortBy(currentSortBy);
        request.setSortOrder(currentSortOrder);

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
                statusLabel.setText(String.format("页面 %d/%d 加载完成。总记录: %d", pageIndex + 1, pagination.getPageCount(), res.getTotalElements()));
            }
        });
        loadTask.setOnFailed(e -> {
            Throwable exception = loadTask.getException();
            if (exception instanceof SocketTimeoutException || exception.getMessage().contains("timeout")) {
                // 3. 超时显示重试按钮，重试逻辑就是再次调用本方法
                loadingManager.showTimeout(() -> loadEmployeeData(pageIndex));
            } else {
                loadingManager.hide();
                CustomAlertDialog.showError("错误", "加载失败");
            }
        });
        new Thread(loadTask).start();
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
        AnchorPane.setTopAnchor(employeeTable, 0.0);
        AnchorPane.setBottomAnchor(employeeTable, 0.0);
        AnchorPane.setLeftAnchor(employeeTable, 0.0);
        AnchorPane.setRightAnchor(employeeTable, 0.0);
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
        if (col == createTimeColumn) return "createTime";
        return null;
    }

    private void setupInServiceColumnFormatting() {
        inServiceColumn.setCellFactory(column -> new TableCell<OaEmployee, Boolean>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("status-active", "status-inactive");
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
            @Override
            protected void updateItem(LocalDateTime it, boolean em) {
                super.updateItem(it, em);
                setText((em || it == null) ? null : AppConstants.dateTimeFormatter(it, AppConstants.YYYY_MM_DD_HH_mm_SS));
            }
        });
    }

    private void setupCenterAlignmentForTextColumn(TableColumn<OaEmployee, String> col) {
        col.setCellFactory(c -> new TableCell<OaEmployee, String>() {
            @Override
            protected void updateItem(String it, boolean em) {
                super.updateItem(it, em);
                setAlignment(Pos.CENTER);
                setText((em || it == null) ? null : it);
            }
        });
    }

    private void setupStatusChoiceBox() {
        statusChoiceBox.getItems().addAll("全部", "在职", "离职");
        statusChoiceBox.setValue("全部");
        statusChoiceBox.getSelectionModel().selectedItemProperty().addListener((o, ol, nv) -> {
            currentInServiceStatus = "在职".equals(nv) ? true : ("离职".equals(nv) ? false : null);
            updatePaginationMetadata();
        });
    }

    @FXML
    private void handleSearch() {
        updatePaginationMetadata();
    }
}