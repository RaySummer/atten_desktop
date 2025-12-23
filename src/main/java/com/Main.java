package com;

import com.ray.atten.desktop.config.AppConfig;
import com.ray.atten.desktop.utils.AppConstants;
import com.ray.atten.desktop.utils.ConfigRepo;
import com.ray.atten.desktop.utils.NetworkUtil;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.IOException;
import java.util.Properties;

public class Main extends Application {

    private ApplicationContext springContext;

    @Override
    public void init() throws Exception {
        // 啟動 Spring 應用上下文，它將掃描 AppConfig 中定義的包
        springContext = new AnnotationConfigApplicationContext(AppConfig.class);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {

        // 1. 尝试读取本地配置
        Properties props = ConfigRepo.loadConfig();
        String protocol = props.getProperty("server.protocol");
        String host = props.getProperty("server.host");
        String portText = props.getProperty("server.port");

        // 2. 判断是否有旧配置且能连接成功
        if (host != null && !host.isEmpty()) {
            int port = Integer.parseInt(portText);
            // 注意：这里的 checkConnection 是阻塞的，JDK 8 启动时允许短时间阻塞
            if (NetworkUtil.checkConnection(protocol.replace("://", ""), host, port)) {
                // 连接成功：更新常量并直接进入主界面
                AppConstants.updateApiBaseUrl(protocol, host, portText);
                showView(primaryStage, "/view/MainView.fxml");
                return;
            }
        }

        // 3. 如果没配置或连接失败，进入设置界面
        showView(primaryStage, "/view/ServerSetupView.fxml");
    }

    private void showView(Stage stage, String path) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(path));
            loader.setControllerFactory(springContext::getBean); // 关键：Spring 注入
            Parent root = loader.load();
            Scene scene = new Scene(root);
            stage.setScene(scene);

            String iconPath = "/images/logo-50.png"; // 请务必确认此路径与 resources 下一致
            var is50 = getClass().getResourceAsStream(iconPath);
            var isOrg = getClass().getResourceAsStream("/images/logo.png");
            if (is50 != null) {
                stage.getIcons().clear();
                stage.getIcons().add(new Image(is50));
                stage.getIcons().add(new Image(isOrg));
            } else {
                System.err.println("警告：未找到图标文件 " + iconPath + "，将使用默认图标。");
            }

            stage.initStyle(StageStyle.TRANSPARENT); // 隱藏操作系統的標題欄按鈕，顯得更簡潔
            stage.setTitle("考勤系统 - 服务器配置");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        launch(args);
    }
}
