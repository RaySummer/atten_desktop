package com.ray.atten.desktop.presentation.controller;

import com.ray.atten.desktop.utils.ConfigRepo;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class MainController {

    @FXML
    private StackPane contentArea;

    @FXML
    private BorderPane mainContainer;

    @Autowired
    private ConfigurableApplicationContext springContext;

    /**
     * 初始化方法：程序进入主界面后，默认显示员工列表
     */
    @FXML
    public void initialize() {
        // 自动应用保存的主题样式
        Platform.runLater(() -> {
            String savedTheme = ConfigRepo.getTheme();
            String cssPath = savedTheme.equals("dark") ? "/css/dark.css" : "/css/light.css";

            Scene scene = mainContainer.getScene(); // mainContainer 是你的 BorderPane ID
            if (scene != null) {
                scene.getStylesheets().clear();
                scene.getStylesheets().add(getClass().getResource(cssPath).toExternalForm());
            }
        });
        showEmployeeView();
    }

    /**
     * 加载子视图的通用方法
     */
    private void loadView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            // 必须设置这个，子界面的 Controller 才能使用 Spring 注入的 Service
            loader.setControllerFactory(springContext::getBean);

            Parent view = loader.load();

            // 切换内容：setAll 会移除 contentArea 之前所有的子节点
            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("加载视图失败: " + fxmlPath);
        }
    }

    @FXML
    public void showEmployeeView() {
        // 加载刚才重命名的员工列表界面
        loadView("/view/EmployeeListView.fxml");
    }

    @FXML
    public void showAttendanceView() {
        // 预留：考勤统计界面
        loadView("/view/AttendanceView.fxml");
    }

    @FXML
    public void showSettingView() {
        // 加载之前写好的服务器配置界面
        loadView("/view/SystemSettingsView.fxml");
    }
}