package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.model.AdminUser;
import com.ray.atten.desktop.model.Company;
import com.ray.atten.desktop.service.AdminService;
import com.ray.atten.desktop.service.CompanyService;
import com.ray.atten.desktop.utils.CustomAlertDialog;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class AdminDetailController {

    @FXML private TextField usernameField;
    @FXML private VBox passwordSection;
    @FXML private PasswordField passwordField;
    @FXML private CheckBox superAdminCheck; // 在 FXML 中已改为显示“修改密码”
    @FXML private ListView<Company> companyListView;
    @FXML private Button saveButton;

    @Autowired private AdminService adminService;
    @Autowired private CompanyService companyService;

    private String currentAdminUuid;
    private Runnable onSaveSuccess;

    @FXML
    public void initialize() {
        companyListView.setCellFactory(lv -> new ListCell<Company>() {
            private final CheckBox cb = new CheckBox();
            private ChangeListener<Boolean> activeListener; // 记录当前的监听器

            {
                cb.getStyleClass().add("custom-check-box");
            }

            @Override
            protected void updateItem(Company item, boolean empty) {
                super.updateItem(item, empty);

                // 1. 彻底移除旧的监听器，防止复用导致的逻辑污染
                if (activeListener != null && getItem() != null) {
                    cb.selectedProperty().removeListener(activeListener);
                }

                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    // 2. 初始化 CheckBox 状态（同步数据模型）
                    cb.setSelected(item.isSelected());
                    cb.setText(item.getName());

                    // 3. 创建并添加新的监听器
                    // 当 UI (CheckBox) 被点击改变时，同步修改数据模型 (Company)
                    activeListener = (obs, oldVal, newVal) -> {
                        item.setSelected(newVal);
                    };
                    cb.selectedProperty().addListener(activeListener);

                    setGraphic(cb);
                }
            }
        });

        // 密码框动态显示逻辑保持不变
        superAdminCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            passwordSection.setVisible(newVal);
            passwordSection.setManaged(newVal);
            if (!newVal) {
                passwordField.clear();
            }
        });
    }

    public void initData(AdminUser admin) {
        // 每次进入页面先清空旧数据，防止状态残留
        companyListView.getItems().clear();

        Task<List<Company>> loadTask = new Task<List<Company>>() {
            @Override
            protected List<Company> call() throws Exception {
                return companyService.getAllCompanies();
            }
        };

        loadTask.setOnSucceeded(e -> {
            List<Company> allCompanies = loadTask.getValue();
            // 在 UI 线程填充所有可选公司
            companyListView.getItems().setAll(allCompanies);

            if (admin == null || admin.getUuid() == null) {
                prepareForAdd();
            } else {
                prepareForEdit(admin);
            }
        });

        loadTask.setOnFailed(e -> {
            log.error("加载公司列表失败", loadTask.getException());
            CustomAlertDialog.showError("数据加载失败", "无法获取公司列表");
        });

        new Thread(loadTask).start();
    }

    private void prepareForAdd() {
        currentAdminUuid = null;
        usernameField.setText("");
        usernameField.setEditable(true);

        // 新增模式：密码框必须存在
        superAdminCheck.setVisible(false);  // 新增时不显示“修改密码”勾选框
        superAdminCheck.setManaged(false);
        superAdminCheck.setSelected(true);  // 强制触发 listener 显示密码框

        passwordField.setPromptText("新管理员必须设置初始密码");
    }

    private void prepareForEdit(AdminUser admin) {
        currentAdminUuid = admin.getUuid().toString();
        usernameField.setText(admin.getUsername());
        usernameField.setEditable(false);

        // 修改模式：显示“修改密码”勾选框，初始不显示密码输入框
        superAdminCheck.setVisible(true);
        superAdminCheck.setManaged(true);
        superAdminCheck.setSelected(false); // 默认隐藏密码输入

        passwordField.setPromptText("留空表示不修改密码");

        // --- 3. 自动勾选该管理员管理的公司 ---
        if (admin.getManagedCompanies() != null) {
            // 提取管理员已有的公司 UUID 集合
            Set<UUID> ownedUuids = admin.getManagedCompanies().stream()
                    .map(Company::getUuid)
                    .collect(Collectors.toSet());

            // 遍历 ListView 中的所有公司（这些是从后端加载的完整清单）
            for (Company c : companyListView.getItems()) {
                if (ownedUuids.contains(c.getUuid())) {
                    c.setSelected(true); // 修改模型属性，Cell 中的单向绑定会自动勾选 CheckBox
                } else {
                    c.setSelected(false);
                }
            }
        }
    }

    @FXML
    private void handleSave() {
        // 1. 基础校验
        if (usernameField.getText().trim().isEmpty()) {
            CustomAlertDialog.showWarning("校验", "用户名不能为空");
            return;
        }

        // 2. 构造 VO 对象
        AdminUser vo = new AdminUser();
        vo.setUsername(usernameField.getText().trim());

        // 收集已选中的公司
        List<Company> selectedCompanies = companyListView.getItems().stream()
                .filter(Company::isSelected)
                .collect(Collectors.toList());
        vo.setManagedCompanies(selectedCompanies);

        // 密码逻辑
        if (superAdminCheck.isSelected() && !passwordField.getText().trim().isEmpty()) {
            vo.setPassword(passwordField.getText().trim());
        }

        if (currentAdminUuid != null) {
            vo.setUuid(UUID.fromString(currentAdminUuid));
        }

        // 3. 执行异步保存任务
        saveButton.setDisable(true);
        Task<Void> saveTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                if (currentAdminUuid == null) {
                    adminService.createAdmin(vo);
                } else {
                    adminService.updateAdmin(vo);
                }
                return null;
            }
        };

        // --- 核心修改：增加保存成功提示 ---
        saveTask.setOnSucceeded(e -> {
            saveButton.setDisable(false);
            // 弹出提示框
            CustomAlertDialog.showInfo("操作成功", "管理员信息已保存");

            // 执行回调（刷新列表并关闭侧边栏）
            if (onSaveSuccess != null) {
                onSaveSuccess.run();
            }
        });

        saveTask.setOnFailed(e -> {
            saveButton.setDisable(false);
            log.error("保存管理员失败", saveTask.getException());
            // 这里已经有失败提示了
            CustomAlertDialog.showError("操作失败", "保存失败：" + saveTask.getException().getMessage());
        });

        new Thread(saveTask).start();
    }

    @FXML
    public void handleCancel() {
        if (onSaveSuccess != null) onSaveSuccess.run();
    }

    public void setOnSaveSuccess(Runnable onSaveSuccess) {
        this.onSaveSuccess = onSaveSuccess;
    }
}