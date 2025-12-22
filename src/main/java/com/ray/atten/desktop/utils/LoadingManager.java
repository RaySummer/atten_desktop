package com.ray.atten.desktop.utils;

import com.ray.atten.desktop.presentation.controller.LoadingController;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.StackPane;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoadingManager {
    private StackPane loadingPane;
    private LoadingController controller;
    private static StackPane rootStackPane; // MainView 里的根容器

    @Autowired
    private ApplicationContext springContext;

    // 在 MainController 初始化时调用此方法绑定根容器
    public void init(StackPane root) {
        rootStackPane = root;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/component/LoadingView.fxml"));
            loader.setControllerFactory(springContext::getBean);
            loadingPane = loader.load();
            controller = loader.getController();
            loadingPane.setVisible(false);
            rootStackPane.getChildren().add(loadingPane);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void show(String message) {
        Platform.runLater(() -> {
            controller.reset();
            controller.setMessage(message);
            loadingPane.setVisible(true);
        });
    }

    public void showTimeout(Runnable retryAction) {
        Platform.runLater(() -> {
            controller.showError("网络请求超时", retryAction);
        });
    }

    public void hide() {
        Platform.runLater(() -> loadingPane.setVisible(false));
    }
}