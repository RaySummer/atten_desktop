package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.model.OaEmployee;
import com.ray.atten.desktop.service.CardTemplateService;
import com.ray.atten.desktop.utils.AppConstants;
import com.ray.atten.desktop.utils.CustomAlertDialog;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class BadgePrintController {

    @Autowired
    private CardTemplateService cardTemplateService;

    @FXML
    private VBox rootPane;
    @FXML
    private ComboBox<Map<String, String>> templateComboBox; // 存放模板名称和UUID
    @FXML
    private Button previewButton;
    @FXML
    private Label countLabel;

    // --- TableView 控件 ---
    @FXML
    private TableView<OaEmployee> employeeTableView;
    @FXML
    private TableColumn<OaEmployee, Integer> rowNumberColumn;
    @FXML
    private TableColumn<OaEmployee, String> pinColumn, nameColumn, deptColumn, postColumn, entryDateColumn;
    @FXML
    private TableColumn<OaEmployee, Void> actionColumn;

    private ObservableList<OaEmployee> selectedEmployees = FXCollections.observableArrayList();
    private Runnable onCloseRequest;

    @FXML
    public void initialize() {
        setupTableColumns();
        loadTemplates();

        // 监听模板选择，只有选择了模板才能点预览
        templateComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            previewButton.setDisable(newVal == null);
        });
    }

    private void setupTableColumns() {
        rowNumberColumn.setCellFactory(col -> new TableCell<OaEmployee, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(getIndex() + 1));
            }
        });

        pinColumn.setCellValueFactory(new PropertyValueFactory<>("pin"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        deptColumn.setCellValueFactory(new PropertyValueFactory<>("dept"));
        postColumn.setCellValueFactory(new PropertyValueFactory<>("post"));
        entryDateColumn.setCellValueFactory(new PropertyValueFactory<>("entryDate"));

        // 操作列：移除按钮
        actionColumn.setCellFactory(col -> new TableCell<OaEmployee, Void>() {
            private final Button btn = new Button("移除");

            {
                btn.getStyleClass().add("action-btn-delete");
                btn.setStyle("-fx-text-fill: white; -fx-background-color: #ff4d4f;");
                btn.setOnAction(e -> {
                    selectedEmployees.remove(getTableView().getItems().get(getIndex()));
                    updateCountLabel();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        employeeTableView.setItems(selectedEmployees);
    }

    /**
     * 加载所有可用的工牌模板
     */
    private void loadTemplates() {
        try {
            // 假设后端返回 List<CardTemplate>，我们将其转为 Map 存入 ComboBox
            List<Map<String, String>> templates = cardTemplateService.getActiveTemplates();
            templateComboBox.setItems(FXCollections.observableArrayList(templates));

            // 设置显示模板名称
            templateComboBox.setCellFactory(param -> new ListCell<Map<String, String>>() {
                @Override
                protected void updateItem(Map<String, String> item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item.get("name"));
                }
            });
            templateComboBox.setButtonCell(new ListCell<Map<String, String>>() {
                @Override
                protected void updateItem(Map<String, String> item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item.get("name"));
                }
            });
        } catch (Exception e) {
            CustomAlertDialog.showError("错误", "无法加载模板列表");
        }
    }

    /**
     * 生成预览并打开浏览器
     */
    @FXML
    private void handleGeneratePreview() {
        Map<String, String> selectedTemplate = templateComboBox.getValue();
        if (selectedTemplate == null || selectedEmployees.isEmpty()) return;

        try {
            // 1. 调用 Service 获取 Ticket (内部请求了 /prepare-print)
            String ticket = cardTemplateService.createPrintTicket(
                    selectedTemplate.get("uuid"),
                    selectedEmployees
            );

            // 2. 构造跳转地址
            // 后端 go-print 接口会自动 redirect 到 badge_print.html
            String jumpUrl = AppConstants.getGotoPrintAPI() + "?ticket=" + ticket;
            System.out.println(jumpUrl);
            // 3. 打开浏览器
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(jumpUrl));
            }

            // 4. 关闭侧边抽屉
            if (onCloseRequest != null) onCloseRequest.run();

        } catch (Exception e) {
            e.printStackTrace();
            com.ray.atten.desktop.utils.CustomAlertDialog.showError("生成失败", "生成失败");
        }
    }

    public void setSelectedEmployees(List<OaEmployee> employees) {
        this.selectedEmployees.setAll(employees);
        updateCountLabel();
    }

    private void updateCountLabel() {
        countLabel.setText("已选择 " + selectedEmployees.size() + " 位员工");
        previewButton.setDisable(selectedEmployees.isEmpty() || templateComboBox.getValue() == null);
    }

    public void setOnCloseRequest(Runnable onCloseRequest) {
        this.onCloseRequest = onCloseRequest;
    }
}