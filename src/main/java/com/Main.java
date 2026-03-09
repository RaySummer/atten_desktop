package com;

import com.ray.atten.desktop.config.AppConfig;
import com.ray.atten.desktop.service.AuthService;
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
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Locale;
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
        Locale.setDefault(Locale.CHINESE);
        Properties props = ConfigRepo.loadConfig();
        String host = props.getProperty("server.host");

        // 第一步：检查服务器配置
        if (StringUtils.hasText(host)) {
            String protocol = props.getProperty("server.protocol");
            String portText = props.getProperty("server.port");

            if (NetworkUtil.checkConnection(protocol.replace("://", ""), host, Integer.parseInt(portText))) {
                AppConstants.updateApiBaseUrl(protocol, host, portText);

                // 2. 尝试自动登录
                String localToken = ConfigRepo.getToken();
                AuthService authService = springContext.getBean(AuthService.class);

                if (authService.checkTokenAndLogin(localToken)) {
                    // Token 有效，直接进主界面
                    showView(primaryStage, "/view/MainView.fxml", "主界面");
                } else {
                    // Token 失效或不存在，进登录界面
                    showView(primaryStage, "/view/LoginView.fxml", "系统登录");
                }

                return;
            }
        }

        // 第三步：服务器不通，去设置界面
        showView(primaryStage, "/view/ServerSetupView.fxml", "服务器配置");
    }

    private void showView(Stage stage, String path, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(path));
            loader.setControllerFactory(springContext::getBean); // 关键：Spring 注入
            Parent root = loader.load();
            // 如果已经有 Scene 就换 Root，没有就新建（防止重复创建 Stage）
            if (stage.getScene() == null) {
                Scene scene = new Scene(root);
                stage.setScene(scene);
            } else {
                stage.getScene().setRoot(root);
            }

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
            stage.setTitle(title);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        launch(args);
    }
}
