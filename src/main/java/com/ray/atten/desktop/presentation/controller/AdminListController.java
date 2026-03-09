package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.model.AdminUser;
import com.ray.atten.desktop.model.Company;
import com.ray.atten.desktop.model.OaEmployee;
import com.ray.atten.desktop.service.AdminService;
import com.ray.atten.desktop.utils.AppConstants;
import com.ray.atten.desktop.utils.CustomAlertDialog;
import javafx.animation.TranslateTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class AdminListController {

    @FXML
    private StackPane rootPane;
    @FXML
    private AnchorPane drawerPane;
    @FXML
    private VBox drawerContent;
    @FXML
    private Label drawerTitleLabel;
    @FXML
    private StackPane formContainer;

    // 列表相关
    @FXML
    private TableView<AdminUser> adminTable;
    @FXML
    private TableColumn<AdminUser, String> colUsername;
    @FXML
    private TableColumn<AdminUser, String> colIsSuper;
    @FXML
    private TableColumn<AdminUser, String> colCompanies;
    @FXML
    private TableColumn<AdminUser, LocalDateTime> colCreateTime;
    @FXML
    private TableColumn<AdminUser, Void> colAction; // 操作列

    //    @FXML
//    private TextField searchField;
    @FXML
    private Label statusLabel;

    @Autowired
    private ApplicationContext springContext;
    @Autowired
    private AdminService adminService;

    @FXML
    public void initialize() {
        // 1. 初始化表格列绑定及操作按钮
        setupTableColumns();
        setupDateTimeColumnFormatting(colCreateTime);
        // 2. 初始将抽屉隐藏在右侧屏幕外
        drawerContent.setTranslateX(450);
        drawerPane.setVisible(false);

        // 3. 表格双击事件：双击行直接修改
        adminTable.setRowFactory(tv -> {
            TableRow<AdminUser> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    showDrawer(row.getItem());
                }
            });
            return row;
        });

        // 4. 首次进入页面加载数据
        loadData();
    }

    /**
     * 配置表格列及操作按钮
     */
    private void setupTableColumns() {
        // 用户名列
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));

        // 超级管理员显示为 是/否
        colIsSuper.setCellValueFactory(cellData -> {
            Boolean isSuper = cellData.getValue().getIsSuperAdmin();
            return new SimpleStringProperty(isSuper != null && isSuper ? "是" : "否");
        });

        // 管辖公司显示为逗号隔开的字符串
        colCompanies.setCellValueFactory(cellData -> {
            List<Company> list = cellData.getValue().getManagedCompanies();
            if (list == null || list.isEmpty()) return new SimpleStringProperty("-");
            String names = list.stream().map(Company::getName).collect(Collectors.joining(", "));
            return new SimpleStringProperty(names);
        });

        // 创建时间列
        colCreateTime.setCellValueFactory(new PropertyValueFactory<>("createTime"));

        // --- 操作列：自定义渲染修改按钮 ---
        colAction.setCellFactory(param -> new TableCell<AdminUser, Void>() {
            private final Button editBtn = new Button("修改");

            {
                editBtn.getStyleClass().add("action-btn-detail");
                editBtn.setStyle("-fx-text-fill: #ffffff;");
                editBtn.setOnAction(event -> {
                    AdminUser data = getTableView().getItems().get(getIndex());
                    showDrawer(data);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(editBtn);
                    setAlignment(Pos.CENTER);
                }
            }
        });
    }

    /**
     * 加载/搜索数据
     */
    @FXML
    private void loadData() {
//        String query = searchField.getText() == null ? "" : searchField.getText().trim();
        String query = "";
        Task<List<AdminUser>> task = new Task<List<AdminUser>>() {
            @Override
            protected List<AdminUser> call() throws Exception {
                return adminService.getAllAdmins(query);
            }
        };

        task.setOnSucceeded(e -> {
            List<AdminUser> list = task.getValue();
            adminTable.getItems().setAll(list);
            statusLabel.setText("共 " + list.size() + " 条记录");
        });

        task.setOnFailed(e -> {
            CustomAlertDialog.showError("加载失败", "无法获取管理员列表");
        });

        new Thread(task).start();
    }

    @FXML
    private void handleSearch() {
        loadData();
    }

    /**
     * 打开侧边抽屉
     *
     * @param data 为空则表示“新增”，不为空则表示“修改”
     */
    private void showDrawer(AdminUser data) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/AdminDetailView.fxml"));
            // 关键：让 Spring 管理 DetailController 的创建
            loader.setControllerFactory(springContext::getBean);
            Parent form = loader.load();

            AdminDetailController controller = loader.getController();

            // 设置保存成功后的回调逻辑
            controller.setOnSaveSuccess(() -> {
                closeDrawer(); // 1. 关闭抽屉
                loadData();    // 2. 刷新表格数据
            });

            // 将表单放入容器
            formContainer.getChildren().setAll(form);

            // 初始化详情页数据
            if (data == null) {
//                drawerTitleLabel.setText("新增管理员");
                controller.initData(null);
            } else {
//                drawerTitleLabel.setText("编辑管理员: " + data.getUsername());
                controller.initData(data);
            }

            // 执行展开动画
            drawerPane.setVisible(true);
            TranslateTransition tt = new TranslateTransition(Duration.millis(500), drawerContent);
            tt.setToX(0);
            tt.play();
        } catch (IOException e) {
            CustomAlertDialog.showError("系统错误", "无法加载详情配置页面");
        }
    }

    private void setupDateTimeColumnFormatting(TableColumn<AdminUser, LocalDateTime> col) {
        col.setCellFactory(c -> new TableCell<AdminUser, LocalDateTime>() {
            @Override protected void updateItem(LocalDateTime it, boolean em) {
                super.updateItem(it, em);
                setText((em || it == null) ? null : AppConstants.dateTimeFormatter(it, AppConstants.YYYY_MM_DD_HH_mm_SS));
            }
        });
    }

    @FXML
    private void closeDrawer() {
        TranslateTransition tt = new TranslateTransition(Duration.millis(300), drawerContent);
        tt.setToX(450);
        tt.setOnFinished(e -> drawerPane.setVisible(false));
        tt.play();
    }

    @FXML
    private void handleAddAdmin() {
        showDrawer(null);
    }

    @FXML
    private void handleOverlayClick(MouseEvent event) {
        // 如果点击的是抽屉以外的透明区域，则关闭抽屉
        if (event.getX() < (drawerPane.getWidth() - drawerContent.getWidth())) {
            closeDrawer();
        }
    }
}