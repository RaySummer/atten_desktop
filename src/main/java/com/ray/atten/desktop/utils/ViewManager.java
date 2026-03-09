package com.ray.atten.desktop.utils;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.context.ApplicationContext;

import java.io.IOException;

public class ViewManager {

    public static void switchView(Stage stage, String fxmlPath, ApplicationContext context, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(ViewManager.class.getResource(fxmlPath));
            loader.setControllerFactory(context::getBean);
            Parent root = loader.load();

            Scene scene = stage.getScene();
            if (scene == null) {
                stage.setScene(new Scene(root));
            } else {
                scene.setRoot(root);
            }

            stage.setTitle(title);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
