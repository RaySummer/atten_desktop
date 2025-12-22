package com.ray.atten.desktop.presentation.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import org.springframework.stereotype.Component;

@Component
public class LoadingController {
    @FXML
    private Label msgLabel;
    @FXML private ProgressIndicator spinner;
    @FXML private Button retryBtn;

    private Runnable retryAction;

    public void setMessage(String msg) { msgLabel.setText(msg); }

    public void reset() {
        spinner.setVisible(true);
        spinner.setManaged(true);
        retryBtn.setVisible(false);
        retryBtn.setManaged(false);
    }

    public void showError(String msg, Runnable retry) {
        msgLabel.setText(msg);
        spinner.setVisible(false);
        spinner.setManaged(false);
        retryBtn.setVisible(true);
        retryBtn.setManaged(true);
        this.retryAction = retry;
    }

    @FXML
    private void handleRetry() {
        if (retryAction != null) {
            reset();
            retryAction.run();
        }
    }
}