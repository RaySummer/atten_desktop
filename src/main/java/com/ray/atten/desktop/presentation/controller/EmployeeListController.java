package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.dto.OaEmployeeQueryRequest;
import com.ray.atten.desktop.dto.PageResponse;
import com.ray.atten.desktop.model.OaEmployee;
import com.ray.atten.desktop.service.OaEmployeeService;
import com.ray.atten.desktop.utils.AppConstants;
import com.ray.atten.desktop.utils.CustomAlertDialog;
import com.ray.atten.desktop.utils.LoadingManager;
import javafx.animation.TranslateTransition;
import javafx.beans.property.SimpleStringProperty;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class EmployeeListController {

    @Autowired
    private OaEmployeeService oaEmployeeService;
    @Autowired
    private ApplicationContext springContext;
    @Autowired
    private LoadingManager loadingManager;

    @FXML
    private AnchorPane drawerPane;
    @FXML
    private VBox drawerContent;
    @FXML
    private StackPane detailContainer;
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
    private ChoiceBox<String> fingerprintChoiceBox;
    @FXML
    private ChoiceBox<String> photoChoiceBox;

    // --- TableView 控件 ---
    private TableView<OaEmployee> employeeTable;
    private TableColumn<OaEmployee, Boolean> selectColumn;
    private TableColumn<OaEmployee, String> pinColumn, nameColumn, companyColumn, deptColumn, post;
    private TableColumn<OaEmployee, String> fingerprintColumn, photoColumn;
    private TableColumn<OaEmployee, Boolean> inServiceColumn;
    private TableColumn<OaEmployee, LocalDateTime> entryDateColumn;
    private TableColumn<OaEmployee, Void> actionColumn;

    private CheckBox selectAllCheckBox;

    // 💡【核心改动1】使用 Map 跨页缓存所有被勾选的员工对象（以 pin 为 Key，可根据你的主键调整，如 id）
    private final Map<String, OaEmployee> selectedEmployeeMap = new HashMap<>();

    private final ObservableList<Integer> pageSizeOptions = FXCollections.observableArrayList(10, 20, 50, 100);
    private long totalRecords = 0;
    private String currentSortBy = "createTime";
    private String currentSortOrder = "DESC";
    private int currentPageSize = AppConstants.DEFAULT_PAGE_SIZE;
    private Boolean currentInServiceStatus = null;
    private Boolean hasFingerprint = null;
    private Boolean hasPhoto = null;

    private Button firstPageBtn;
    private Button lastPageBtn;

    @FXML
    public void initialize() {
        createTableViewStructure();
        setupActionColumn();
        setupInServiceColumnFormatting();
        setupDateTimeColumnFormatting(entryDateColumn);
        setupCenterAlignmentForTextColumn(pinColumn);
        setupCenterAlignmentForTextColumn(nameColumn);
        setupCenterAlignmentForTextColumn(companyColumn);
        setupCenterAlignmentForTextColumn(deptColumn);
        setupCenterAlignmentForTextColumn(post);

        employeeTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        setupPaginationAndControls();
        setupStatusChoiceBox();
        setupSearchFilters();

        updatePaginationMetadata();
        statusLabel.setText("数据列表已就绪。");
    }

    private void createTableViewStructure() {
        employeeTable = new TableView<>();
        employeeTable.getStyleClass().add("custom-table-view");
        employeeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        employeeTable.setEditable(true);

        selectAllCheckBox = new CheckBox();
        selectAllCheckBox.setOnAction(e -> handleSelectAllAction());

        selectColumn = new TableColumn<>();
        selectColumn.setGraphic(selectAllCheckBox);
        selectColumn.setMinWidth(40);
        selectColumn.setMaxWidth(40);

        selectColumn.setCellValueFactory(data -> data.getValue().selectedProperty());
        selectColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectColumn));

        pinColumn = new TableColumn<>("工号");
        nameColumn = new TableColumn<>("姓名");
        companyColumn = new TableColumn<>("公司");
        deptColumn = new TableColumn<>("部门");
        post = new TableColumn<>("职位");
        fingerprintColumn = new TableColumn<>("指纹");
        photoColumn = new TableColumn<>("照片");
        inServiceColumn = new TableColumn<>("是否在职");
        entryDateColumn = new TableColumn<>("入职日期");
        actionColumn = new TableColumn<>("操作");

        fingerprintColumn.setMinWidth(65);
        fingerprintColumn.setMaxWidth(80);
        photoColumn.setMinWidth(65);
        photoColumn.setMaxWidth(80);
        entryDateColumn.setMinWidth(70);
        companyColumn.setMinWidth(120);
        actionColumn.setMinWidth(100);

        pinColumn.setCellValueFactory(new PropertyValueFactory<>("pin"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        companyColumn.setCellValueFactory(new PropertyValueFactory<>("company"));
        deptColumn.setCellValueFactory(new PropertyValueFactory<>("dept"));
        post.setCellValueFactory(new PropertyValueFactory<>("post"));
        fingerprintColumn.setCellValueFactory(data -> {
            OaEmployee emp = data.getValue();
            boolean hasFinger = emp.getSyncList() != null && emp.getSyncList().stream()
                    .anyMatch(s -> "finger".equals(s.getType()) && s.getBase64Data() != null && !s.getBase64Data().isEmpty());
            return new SimpleStringProperty(hasFinger ? "EXISTS" : "");
        });
        photoColumn.setCellValueFactory(data -> {
            OaEmployee emp = data.getValue();
            boolean hasPhoto = (emp.getPhotoBase64() != null && !emp.getPhotoBase64().isEmpty()) ||
                    (emp.getSyncList() != null && emp.getSyncList().stream()
                            .anyMatch(s -> "photo".equals(s.getType()) && s.getBase64Data() != null && !s.getBase64Data().isEmpty()));
            return new SimpleStringProperty(hasPhoto ? "EXISTS" : "");
        });
        inServiceColumn.setCellValueFactory(new PropertyValueFactory<>("inService"));
        entryDateColumn.setCellValueFactory(new PropertyValueFactory<>("entryDate"));

        setupBinaryStatusColumnFormatting(fingerprintColumn);
        setupBinaryStatusColumnFormatting(photoColumn);

        employeeTable.getColumns().addAll(selectColumn, pinColumn, nameColumn, companyColumn, deptColumn, post,
                fingerprintColumn, photoColumn, inServiceColumn, entryDateColumn, actionColumn);

        for (TableColumn<OaEmployee, ?> col : employeeTable.getColumns()) {
            col.setSortable(false);
        }
    }

    private void handleSelectAllAction() {
        boolean isSelected = selectAllCheckBox.isSelected();
        employeeTable.getItems().forEach(emp -> {
            emp.setSelected(isSelected);
            if (isSelected) {
                selectedEmployeeMap.put(emp.getPin(), emp);
            } else {
                selectedEmployeeMap.remove(emp.getPin());
            }
        });
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
                List<OaEmployee> pageList = res.getContent();

                // 💡【核心改动2】数据加载后，回显之前选中的状态，并绑定监听器
                for (OaEmployee emp : pageList) {
                    // 如果跨页缓存中包含该工号，恢复选中状态
                    if (selectedEmployeeMap.containsKey(emp.getPin())) {
                        emp.setSelected(true);
                    } else {
                        emp.setSelected(false);
                    }

                    // 监听当前页每一行的勾选改变，同步更新缓存
                    emp.selectedProperty().addListener((observable, oldValue, newValue) -> {
                        if (Boolean.TRUE.equals(newValue)) {
                            selectedEmployeeMap.put(emp.getPin(), emp);
                        } else {
                            selectedEmployeeMap.remove(emp.getPin());
                        }
                        // 动态更新表头的“全选框”状态
                        updateSelectAllCheckBoxState();
                    });
                }

                employeeTable.setItems(FXCollections.observableArrayList(pageList));
                updateSelectAllCheckBoxState(); // 更新全选复选框

                statusLabel.setText(String.format("页面 %d/%d 加载完成。总记录: %d | 当前已勾选: %d 人",
                        pageIndex + 1, pagination.getPageCount(), res.getTotalElements(), selectedEmployeeMap.size()));
            }
        });

        loadTask.setOnFailed(e -> {
            loadingManager.hide();
            CustomAlertDialog.showError("错误", "加载员工数据失败");
        });

        new Thread(loadTask).start();
    }

    // 💡【新增方法】根据当前页勾选情况自动刷新表头全选框的状态
    private void updateSelectAllCheckBoxState() {
        ObservableList<OaEmployee> items = employeeTable.getItems();
        if (items == null || items.isEmpty()) {
            selectAllCheckBox.setSelected(false);
            return;
        }
        boolean allSelected = items.stream().allMatch(OaEmployee::isSelected);
        selectAllCheckBox.setSelected(allSelected);
    }

    // 💡【核心改动3】从跨页缓存选中的 Map 中获取所有选中的员工，而不是仅从当前页表格中读取
    @FXML
    private void handleBatchSynchronize() {
        List<OaEmployee> selected = new ArrayList<>(selectedEmployeeMap.values());

        if (selected.isEmpty()) {
            CustomAlertDialog.showWarning(null, "请勾选至少一位员工。");
            return;
        }
        openViewInDrawer("/view/SyncGroupView.fxml", selected);
    }

    // 💡【核心改动4】同样支持跨页选中的打印处理
    @FXML
    private void handlePrintBadge() {
        List<OaEmployee> selected = new ArrayList<>(selectedEmployeeMap.values());

        if (selected.isEmpty()) {
            CustomAlertDialog.showWarning(null, "请勾选至少一位员工。");
            return;
        }
        openViewInDrawer("/view/BadgePrintView.fxml", selected);
    }

    private void setupSearchFilters() {
        fingerprintChoiceBox.getItems().addAll("全部", "已有", "未录");
        fingerprintChoiceBox.setValue("全部");
        fingerprintChoiceBox.getSelectionModel().selectedItemProperty().addListener((o, ol, nv) -> {
            switch (nv) {
                case "已有":
                    hasFingerprint = true;
                    break;
                case "未录":
                    hasFingerprint = false;
                    break;
                default:
                    hasFingerprint = null;
                    break;
            }
            updatePaginationMetadata();
        });

        photoChoiceBox.getItems().addAll("全部", "已有", "未录");
        photoChoiceBox.setValue("全部");
        photoChoiceBox.getSelectionModel().selectedItemProperty().addListener((o, ol, nv) -> {
            switch (nv) {
                case "已有":
                    hasPhoto = true;
                    break;
                case "未录":
                    hasPhoto = false;
                    break;
                default:
                    hasPhoto = null;
                    break;
            }
            updatePaginationMetadata();
        });
    }

    private void fillRequestParams(OaEmployeeQueryRequest request) {
        request.setKeyword(queryField.getText().trim());
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
                int calculatedPages = (int) Math.ceil((double) totalRecords / currentPageSize);
                int pageCount = Math.max(1, calculatedPages);

                // 1. 优先设置正确的页数
                pagination.setPageCount(pageCount);

                // 2. 修正当前页码索引，防止超过最大页数
                int current = pagination.getCurrentPageIndex();
                if (current >= pageCount) {
                    current = pageCount - 1;
                    pagination.setCurrentPageIndex(current);
                }

                // 3. 强制刷新当前页内容及指示器状态
                loadEmployeeData(current);
                updatePageButtonStates(current);
            }
        });

        metadataTask.setOnFailed(e -> {
            loadingManager.hide();
            CustomAlertDialog.showError("错误", "加载元数据失败");
        });

        new Thread(metadataTask).start();
    }

    private void setupBinaryStatusColumnFormatting(TableColumn<OaEmployee, String> col) {
        col.setCellFactory(column -> new TableCell<OaEmployee, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    setAlignment(Pos.CENTER);
                    boolean exists = (item != null && !item.isEmpty());

                    if (exists) {
                        setText("是");
                        setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: bold;");
                    } else {
                        setText("否");
                        setStyle("-fx-text-fill: #ffa89c;");
                    }
                }
            }
        });
    }

    private void setupStatusChoiceBox() {
        statusChoiceBox.getItems().addAll("全部", "在职", "离职");
        statusChoiceBox.setValue("全部");
        statusChoiceBox.getSelectionModel().selectedItemProperty().addListener((o, ol, nv) -> {
            switch (nv) {
                case "在职":
                    currentInServiceStatus = true;
                    break;
                case "离职":
                    currentInServiceStatus = false;
                    break;
                default:
                    currentInServiceStatus = null;
                    break;
            }
            // 重新搜索或切换筛选条件时，可根据需求选择是否清空已选集合
            // selectedEmployeeMap.clear();
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

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : pane);
            }
        });
    }

    private void setupInServiceColumnFormatting() {
        inServiceColumn.setCellFactory(column -> new TableCell<OaEmployee, Boolean>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
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
            @Override
            protected void updateItem(LocalDateTime it, boolean em) {
                super.updateItem(it, em);
                setText((em || it == null) ? null : AppConstants.dateTimeFormatter(it, AppConstants.YYYY_MM_DD));
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

    private void setupPaginationAndControls() {
        pageSizeComboBox.setValue(currentPageSize);
        pageSizeComboBox.setItems(pageSizeOptions);
        pageSizeComboBox.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                currentPageSize = newVal;
                pagination.setCurrentPageIndex(0);
                updatePaginationMetadata();
            }
        });

        // 设置页面工厂
        pagination.setPageFactory(this::createPage);

        // 💡 延迟注入“首页”和“末页”按钮
        tryInjectFirstAndLastButtons(0);
    }

    /**
     * 动态查找 JavaFX Pagination 内部的 HBox 并注入“首页”和“末页”按钮
     */
    private void tryInjectFirstAndLastButtons(int retryCount) {
        javafx.application.Platform.runLater(() -> {
            Node controlBox = pagination.lookup(".control-box");

            // 如果 Pagination 还没完成 DOM 树渲染，使用 PauseTransition 延迟 100ms 重试
            if (controlBox == null) {
                if (retryCount < 10) { // 最多重试 10 次
                    javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(Duration.millis(100));
                    delay.setOnFinished(event -> tryInjectFirstAndLastButtons(retryCount + 1));
                    delay.play();
                }
                return;
            }

            if (controlBox instanceof HBox) {
                HBox hbox = (HBox) controlBox;

                // 检查是否已经注入过，避免重复插入
                if (hbox.getChildren().stream().anyMatch(node -> "first-page-btn".equals(node.getId()))) {
                    return;
                }

                // 1. 创建【首页】按钮
                firstPageBtn = new Button("首页");
                firstPageBtn.setId("first-page-btn");
                firstPageBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #333333; -fx-cursor: hand; -fx-padding: 4 8;");
                firstPageBtn.setOnAction(e -> {
                    if (pagination.getCurrentPageIndex() != 0) {
                        pagination.setCurrentPageIndex(0);
                    }
                });

                // 2. 创建【末页】按钮
                lastPageBtn = new Button("末页");
                lastPageBtn.setId("last-page-btn");
                lastPageBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #333333; -fx-cursor: hand; -fx-padding: 4 8;");
                lastPageBtn.setOnAction(e -> {
                    int totalPages = pagination.getPageCount();
                    if (totalPages > 0 && pagination.getCurrentPageIndex() != totalPages - 1) {
                        pagination.setCurrentPageIndex(totalPages - 1);
                    }
                });

                // 3. 动态插入到工具栏中
                hbox.getChildren().add(0, firstPageBtn);
                hbox.getChildren().add(lastPageBtn);

                // 4. 刷新按钮启用/禁用状态
                updatePageButtonStates(pagination.getCurrentPageIndex());
            }
        });
    }

    /**
     * 更新“首页”、“末页”及边界按钮状态，解决最后一页还能再点下一页的 Bug
     */
    private void updatePageButtonStates(int pageIndex) {
        int totalPages = pagination.getPageCount();

        if (firstPageBtn != null) {
            boolean isFirst = (pageIndex <= 0);
            firstPageBtn.setDisable(isFirst);
            firstPageBtn.setOpacity(isFirst ? 0.4 : 1.0);
        }
        if (lastPageBtn != null) {
            boolean isLast = (pageIndex >= totalPages - 1 || totalPages <= 1);
            lastPageBtn.setDisable(isLast);
            lastPageBtn.setOpacity(isLast ? 0.4 : 1.0);
        }

        // 强行禁用最后一页的“下一页”和第一页的“上一页”点击响应，彻底解决越界问题
        Node nextBtn = pagination.lookup(".next-button");
        if (nextBtn != null) {
            boolean isLast = (pageIndex >= totalPages - 1);
            nextBtn.setDisable(isLast);
            nextBtn.setMouseTransparent(isLast);
        }

        Node prevBtn = pagination.lookup(".prev-button");
        if (prevBtn != null) {
            boolean isFirst = (pageIndex <= 0);
            prevBtn.setDisable(isFirst);
            prevBtn.setMouseTransparent(isFirst);
        }
    }

    private Node createPage(int pageIndex) {
        // 💡 校验页码边界，防止超出最大页数
        int totalPages = pagination.getPageCount();
        if (totalPages > 0 && pageIndex >= totalPages) {
            pageIndex = totalPages - 1;
        }

        loadEmployeeData(pageIndex);
        updatePageButtonStates(pageIndex);

        AnchorPane ap = new AnchorPane(employeeTable);
        AnchorPane.setTopAnchor(employeeTable, 0.0);
        AnchorPane.setBottomAnchor(employeeTable, 0.0);
        AnchorPane.setLeftAnchor(employeeTable, 0.0);
        AnchorPane.setRightAnchor(employeeTable, 0.0);
        return ap;
    }

    private void openViewInDrawer(String fxmlPath, Object data) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            loader.setControllerFactory(springContext::getBean);
            Node node = loader.load();
            Object controller = loader.getController();
            if (controller instanceof EmployeeDetailController) {
                ((EmployeeDetailController) controller).setEmployeeInfo((OaEmployee) data);
                ((EmployeeDetailController) controller).setOnCloseRequest(() -> {
                    closeDrawer();
                    loadEmployeeData(pagination.getCurrentPageIndex());
                });
            } else if (controller instanceof SyncGroupController) {
                List<OaEmployee> employees = (data instanceof List) ? (List<OaEmployee>) data : Collections.singletonList((OaEmployee) data);
                ((SyncGroupController) controller).setEmployeesToSync(employees);
                ((SyncGroupController) controller).setOnCloseRequest(() -> {
                    closeDrawer();
                    refreshCurrentPage();
                });
            } else if (controller instanceof BadgePrintController) {
                List<OaEmployee> employees = (data instanceof List) ? (List<OaEmployee>) data : Collections.singletonList((OaEmployee) data);
                ((BadgePrintController) controller).setSelectedEmployees(employees);
                ((BadgePrintController) controller).setOnCloseRequest(this::closeDrawer);
            }
            detailContainer.getChildren().setAll(node);
            showDrawer();
        } catch (IOException e) {
            CustomAlertDialog.showError("", "无法加载界面");
        }
    }

    private void showDrawer() {
        if (drawerPane == null) return;
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
        if (event.getX() < (drawerPane.getWidth() - drawerContent.getWidth())) closeDrawer();
    }

    @FXML
    private void handleSearch() {
        // 如果进行关键词搜索，建议清空先前选中的记录（可根据业务选择保留或清空）
        // selectedEmployeeMap.clear();
        updatePaginationMetadata();
    }

    private void refreshCurrentPage() {
        loadEmployeeData(pagination.getCurrentPageIndex());
    }

}