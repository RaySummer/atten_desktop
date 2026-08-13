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

    // 跨页缓存所有被勾选的员工对象
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
        updateStatusLabelText(pagination.getCurrentPageIndex() + 1);
    }

    // 💡【新增功能】清空所有已勾选的记录（跨页与当前页）
    @FXML
    private void handleClearSelection() {
        if (selectedEmployeeMap.isEmpty()) {
            return;
        }

        // 1. 清空跨页缓存 Map
        selectedEmployeeMap.clear();

        // 2. 取消当前表格中所有渲染对象的选中状态
        if (employeeTable.getItems() != null) {
            employeeTable.getItems().forEach(emp -> emp.setSelected(false));
        }

        // 3. 取消表头的全选框勾选
        if (selectAllCheckBox != null) {
            selectAllCheckBox.setSelected(false);
        }

        // 4. 更新底部状态栏信息
        updateStatusLabelText(pagination.getCurrentPageIndex() + 1);
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

                for (OaEmployee emp : pageList) {
                    if (selectedEmployeeMap.containsKey(emp.getPin())) {
                        emp.setSelected(true);
                    } else {
                        emp.setSelected(false);
                    }

                    emp.selectedProperty().addListener((observable, oldValue, newValue) -> {
                        if (Boolean.TRUE.equals(newValue)) {
                            selectedEmployeeMap.put(emp.getPin(), emp);
                        } else {
                            selectedEmployeeMap.remove(emp.getPin());
                        }
                        updateSelectAllCheckBoxState();
                        updateStatusLabelText(pageIndex + 1);
                    });
                }

                employeeTable.setItems(FXCollections.observableArrayList(pageList));
                updateSelectAllCheckBoxState();
                updateStatusLabelText(pageIndex + 1);
            }
        });

        loadTask.setOnFailed(e -> {
            loadingManager.hide();
            CustomAlertDialog.showError("错误", "加载员工数据失败");
        });

        new Thread(loadTask).start();
    }

    private void updateSelectAllCheckBoxState() {
        ObservableList<OaEmployee> items = employeeTable.getItems();
        if (items == null || items.isEmpty()) {
            selectAllCheckBox.setSelected(false);
            return;
        }
        boolean allSelected = items.stream().allMatch(OaEmployee::isSelected);
        selectAllCheckBox.setSelected(allSelected);
    }

    private void updateStatusLabelText(int currentPage) {
        statusLabel.setText(String.format("页面 %d/%d 加载完成。总记录: %d | 当前已勾选: %d 人",
                currentPage, pagination.getPageCount(), totalRecords, selectedEmployeeMap.size()));
    }

    @FXML
    private void handleBatchSynchronize() {
        List<OaEmployee> selected = new ArrayList<>(selectedEmployeeMap.values());

        if (selected.isEmpty()) {
            CustomAlertDialog.showWarning(null, "请勾选至少一位员工。");
            return;
        }
        openViewInDrawer("/view/SyncGroupView.fxml", selected);
    }

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

                pagination.setPageCount(pageCount);

                int current = pagination.getCurrentPageIndex();
                if (current >= pageCount) {
                    current = pageCount - 1;
                    pagination.setCurrentPageIndex(current);
                }

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

        pagination.setPageFactory(this::createPage);
        tryInjectFirstAndLastButtons(0);
    }

    private void tryInjectFirstAndLastButtons(int retryCount) {
        javafx.application.Platform.runLater(() -> {
            Node controlBox = pagination.lookup(".control-box");

            if (controlBox == null) {
                if (retryCount < 10) {
                    javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(Duration.millis(100));
                    delay.setOnFinished(event -> tryInjectFirstAndLastButtons(retryCount + 1));
                    delay.play();
                }
                return;
            }

            if (controlBox instanceof HBox) {
                HBox hbox = (HBox) controlBox;

                if (hbox.getChildren().stream().anyMatch(node -> "first-page-btn".equals(node.getId()))) {
                    return;
                }

                firstPageBtn = new Button("首页");
                firstPageBtn.setId("first-page-btn");
                firstPageBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #333333; -fx-cursor: hand; -fx-padding: 4 8;");
                firstPageBtn.setOnAction(e -> {
                    if (pagination.getCurrentPageIndex() != 0) {
                        pagination.setCurrentPageIndex(0);
                    }
                });

                lastPageBtn = new Button("末页");
                lastPageBtn.setId("last-page-btn");
                lastPageBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #333333; -fx-cursor: hand; -fx-padding: 4 8;");
                lastPageBtn.setOnAction(e -> {
                    int totalPages = pagination.getPageCount();
                    if (totalPages > 0 && pagination.getCurrentPageIndex() != totalPages - 1) {
                        pagination.setCurrentPageIndex(totalPages - 1);
                    }
                });

                hbox.getChildren().add(0, firstPageBtn);
                hbox.getChildren().add(lastPageBtn);

                updatePageButtonStates(pagination.getCurrentPageIndex());
            }
        });
    }

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
        updatePaginationMetadata();
    }

    private void refreshCurrentPage() {
        loadEmployeeData(pagination.getCurrentPageIndex());
    }

}